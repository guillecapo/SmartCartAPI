package com.msd.smartcart.application.service;

import com.msd.smartcart.domain.error.CartError;
import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.domain.port.in.CartUseCase;
import com.msd.smartcart.domain.port.out.ActiveCartRepository;
import com.msd.smartcart.domain.port.out.CartBackupRepository;
import com.msd.smartcart.domain.port.out.ProductRepository;
import com.msd.smartcart.domain.port.out.AiRecommenderPort;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;
import com.msd.smartcart.shared.exception.InfrastructureException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@Observed(name = "cart.service")   // crea span automático para cada método público
@RequiredArgsConstructor
public class CartService implements CartUseCase {

    private static final BigDecimal BACKUP_VALUE_THRESHOLD = new BigDecimal("500.00");
    private static final int BACKUP_ITEM_THRESHOLD = 20;
    private static final int MAX_PRODUCTS = 50;

    private final ActiveCartRepository activeCartRepository;
    private final CartBackupRepository cartBackupRepository;
    private final ProductRepository productRepository;
    private final AiRecommenderPort aiRecommenderPort;
    private final MeterRegistry meterRegistry;

    @Override
    public Result<Cart, AppError> addItem(String userId, String productId, int quantity) {

        if (quantity <= 0) {
            log.warn("addItem rejected: invalid quantity [userId={}, productId={}, quantity={}]",
                    userId, productId, quantity);
            cartError("invalid_quantity");
            return Result.failure(CartError.InvalidQuantity.of(quantity));
        }

        Cart cart;

        try {
            cart = activeCartRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        log.info("No active cart found, creating new one [userId={}]", userId);
                        return Cart.createFor(userId);
                    });
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }

        Optional<CartItem> existing = cart.items().stream()
                .filter(i -> i.productId().equals(productId))
                .findFirst();

        Cart updatedCart;
        if (existing.isPresent()) {
            updatedCart = cart.add(new CartItem(
                    productId,
                    existing.get().name(),
                    quantity,
                    existing.get().unitPrice()
            ));
        } else {

            if (cart.items().size() >= MAX_PRODUCTS) {
                cartError("cart_overflow");
                return Result.failure(CartError.CartOverflow.of(userId));
            }

            Product product;
            try {
                product = productRepository.findById(productId).orElse(null);
            } catch (Exception e) {
                return switch (e) {
                    case InfrastructureException ex -> {
                        log.error("Infrastructure failure [productId={}] — {}", productId, ex.getMessage());
                        yield Result.failure(AppError.persistence("product.infra.failed", userId));
                    }
                    default -> {
                        log.error("Unexpected error [productId={}] — {}", productId, e.getMessage(), e);
                        throw e;
                    }
                };
            }

            if (product == null) {
                log.warn("addItem: product not found [userId={}, productId={}]", userId, productId);
                cartError("product_not_found");
                return Result.failure(CartError.ProductNotFound.of(productId));
            }
            updatedCart = cart.add(new CartItem(
                    productId,
                    product.name(),
                    quantity,
                    product.unitPrice()
            ));
        }

        try {
            activeCartRepository.save(updatedCart);
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }

        // MongoDB backup — best effort, no aborta el flujo
        if (exceedsThreshold(updatedCart)) {
            try {
                log.info("Cart exceeds threshold, persisting backup [userId={}, totalValue={}, itemCount={}]",
                        userId, updatedCart.totalValue(), updatedCart.items().size());
                cartBackupRepository.saveOrUpdate(updatedCart);
                Counter.builder("cart.backup.triggered_total")
                        .description("Número de veces que se activó el backup de carrito a MongoDB")
                        .register(meterRegistry)
                        .increment();
            } catch (Exception e) {
                log.warn("Backup cart failed (non-critical) [userId={}] — {}", userId, e.getMessage());
                // best-effort — Redis ya tiene el estado correcto
            }
        }

        triggerAiSuggestions(userId, updatedCart);

        // Métricas de éxito
        Counter.builder("cart.items.added_total")
                .description("Total de ítems añadidos al carrito")
                .tag("new_item", String.valueOf(existing.isEmpty()))
                .register(meterRegistry)
                .increment();
        DistributionSummary.builder("cart.size")
                .description("Número de ítems distintos en el carrito tras una operación add")
                .register(meterRegistry)
                .record(updatedCart.items().size());

        log.info("Item added successfully [userId={}, productId={}, quantity={}]",
                userId, productId, quantity);

        return Result.success(updatedCart);
    }

    @Override
    public Result<Cart, AppError> removeItem(String userId, String productId, int quantity) {
        Cart cart;
        try {
            cart = activeCartRepository.findByUserId(userId).orElse(null);
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }


        if (cart == null) {
            log.warn("removeItem: cart not found [userId={}, productId={}]", userId, productId);
            return Result.failure(CartError.NotFound.of(userId));
        }

        Cart updatedCart = cart.remove(productId, quantity);

        try {
            activeCartRepository.save(updatedCart);
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }

        return Result.success(updatedCart);
    }

    @Override
    public Result<Cart, AppError> getCart(String userId) {
        Result<Cart, AppError> result;

        try {
            result = activeCartRepository.findByUserId(userId)
                    .<Result<Cart, AppError>>map(Result::success)
                    .orElseGet(() -> {
                        log.debug("getCart: no active cart [userId={}]", userId);
                        return Result.failure(CartError.NotFound.of(userId));
                    });
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }

        return result;
    }

    @Override
    public Result<Void, AppError> clearCart(String userId) {
        Optional<Cart> cartOpt;

        try {
            cartOpt = activeCartRepository.findByUserId(userId);
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }

        if (cartOpt.isEmpty()) {
            log.warn("clearCart: cart not found [userId={}]", userId);
            return Result.failure(CartError.NotFound.of(userId));
        }

        try {
            activeCartRepository.deleteByUserId(userId);
        } catch (Exception e) {
            return switch (e) {
                case InfrastructureException ex -> {
                    log.error("Infrastructure failure [userId={}] — {}", userId, ex.getMessage());
                    yield Result.failure(AppError.persistence("active.cart.infra.failed", userId));
                }
                default -> {
                    log.error("Unexpected error [userId={}] — {}", userId, e.getMessage(), e);
                    throw e;
                }
            };
        }

        try {
            cartBackupRepository.deleteByUserId(userId);
        } catch (Exception e) {
            log.warn("Backup cart delete failed (non-critical) [userId={}] — {}", userId, e.getMessage());
            // best-effort — el próximo backup sobreescribe si las condiciones se cumplen
        }

        log.info("Cart cleared [userId={}]", userId);
        return Result.success(null);
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private boolean exceedsThreshold(Cart cart) {
        return cart.totalValue().compareTo(BACKUP_VALUE_THRESHOLD) > 0
                || cart.items().size() > BACKUP_ITEM_THRESHOLD;
    }

    private void cartError(String errorType) {
        Counter.builder("cart.errors_total")
                .description("Total de errores en operaciones de carrito por tipo")
                .tag("operation", "add_item")
                .tag("type", errorType)
                .register(meterRegistry)
                .increment();
    }

    private void triggerAiSuggestions(String userId, Cart cart) {
        try {
            aiRecommenderPort.suggest(
                    userId,
                    cart.items().stream().map(CartItem::productId).toList()
            );
        } catch (Exception e) {
            log.warn("AI suggestions failed (non-critical) [userId={}] — {}", userId, e.getMessage());
            // Degradación elegante — las sugerencias AI no son críticas para el flujo de compra
        }
    }
}