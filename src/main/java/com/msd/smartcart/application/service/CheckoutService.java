package com.msd.smartcart.application.service;

import com.msd.smartcart.domain.error.CartError;
import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.domain.port.in.CartUseCase;
import com.msd.smartcart.domain.port.in.CheckoutUseCase;
import com.msd.smartcart.domain.port.out.NotificationPublisher;
import com.msd.smartcart.domain.port.out.OrderRepository;
import com.msd.smartcart.domain.port.out.ProductRepository;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;
import com.msd.smartcart.shared.exception.DuplicateOrderException;
import com.msd.smartcart.shared.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService implements CheckoutUseCase {

    private final CartUseCase cartUseCase;
    private final OrderRepository orderRepository;
    private final NotificationPublisher notificationPublisher;
    private final ProductRepository productRepository;

    @Override
    public Result<CheckoutResult, AppError> checkout(String userId) {

        // 1. Obtener carrito activo
        Result<Cart, AppError> cartResult = cartUseCase.getCart(userId);

        if (cartResult.isFailure()) {
            log.warn("Checkout failed: cart not found [userId={}]", userId);
            return Result.failure(cartResult.getError());
        }
        Cart cart = cartResult.getValue();

        // 2. Validar carrito no vacío
        if (cart.items().isEmpty()) {
            log.warn("Checkout failed: empty cart [userId={}]", userId);
            return Result.failure(CartError.EmptyCart.of(userId));
        }

        // 3. Validar stock de todos los items — lazy, todos a la vez
        List<CheckoutResult.OutOfStockItem> outOfStockItems = validateStock(cart);

        // 4. Auto-corregir carrito si hay productos sin stock
        if (!outOfStockItems.isEmpty()) {
            log.info("Stock validation failed, auto-correcting cart [userId={}, removedItems={}]",
                    userId, outOfStockItems.size());

            outOfStockItems.forEach(item ->
                    cartUseCase.removeItem(userId, item.productId(), item.requestedQuantity())
            );

            Result<Cart, AppError> correctedCartResult = cartUseCase.getCart(userId);

            boolean cartIsEmpty = correctedCartResult.isFailure()
                    || correctedCartResult.getValue().items().isEmpty();

            if (cartIsEmpty) {
                log.warn("Checkout rejected: all items removed after stock validation [userId={}]", userId);
                return Result.failure(CartError.EmptyCart.of(userId));
            }

            // Retorna OutOfStock con la lista de productos removidos ✅
            return Result.success(new CheckoutResult.OutOfStock(outOfStockItems));
        }

        // 5. Generar y persistir orden
        Order order = Order.createFor(cart);
        try {
            orderRepository.save(order);
            log.info("Order persisted [userId={}, orderId={}, total={}]",
                    userId, order.orderId(), order.totalAmount());
        } catch (Exception e) {
            return switch (e) {
                case DuplicateOrderException ex ->
                        Result.failure(AppError.internal("order.duplicate", userId));
                case InfrastructureException ex ->
                        Result.success(new CheckoutResult.InfrastructureFailure("order.infra.failed"));
                default ->
                        Result.success(new CheckoutResult.InfrastructureFailure("order.save.failed"));
            };
        }

        // 6. Limpiar carrito
        cartUseCase.clearCart(userId);
        log.info("Cart cleared post-checkout [userId={}]", userId);

        // 7. Publicar evento — best-effort, reintentos manejados por el adapter
        try {
            notificationPublisher.publish(OrderConfirmedEvent.from(order));
        } catch (Exception e) {
            log.warn("Notification failed (non-critical) [userId={}, orderId={}] — {}",
                    userId, order.orderId(), e.getMessage());
        }

        log.info("Checkout completed [userId={}, orderId={}]", userId, order.orderId());

        // 8. Retornar resultado confirmado
        return Result.success(new CheckoutResult.Confirmed(order));
    }

    private List<CheckoutResult.OutOfStockItem> validateStock(Cart cart) {
        List<CheckoutResult.OutOfStockItem> outOfStock = new ArrayList<>();

        cart.items().forEach(item -> {
            try {
                productRepository.findById(item.productId()).ifPresentOrElse(
                        product -> {
                            if (product.stock() < item.quantity()) {
                                log.warn("Insufficient stock [productId={}, requested={}, available={}]",
                                        item.productId(), item.quantity(), product.stock());
                                outOfStock.add(new CheckoutResult.OutOfStockItem(
                                        item.productId(),
                                        item.name(),
                                        item.quantity(),
                                        product.stock()
                                ));
                            }
                        },
                        () -> {
                            log.warn("Product no longer exists [productId={}]", item.productId());
                            outOfStock.add(new CheckoutResult.OutOfStockItem(
                                    item.productId(),
                                    item.name(),
                                    item.quantity(),
                                    0
                            ));
                        }
                );
            } catch (Exception e) {
                switch (e) {
                    case InfrastructureException ex ->
                            log.error("Infrastructure failure validating stock [productId={}] — {}",
                                    item.productId(), ex.getMessage());
                    default ->
                            log.error("Unexpected error validating stock [productId={}] — {}",
                                    item.productId(), e.getMessage(), e);
                }
                outOfStock.add(new CheckoutResult.OutOfStockItem(
                        item.productId(),
                        item.name(),
                        item.quantity(),
                        0
                ));
            }
        });

        return outOfStock;
    }
}