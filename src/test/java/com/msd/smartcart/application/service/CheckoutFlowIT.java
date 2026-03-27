package com.msd.smartcart.application.service;

import com.msd.smartcart.IntegrationTestBase;
import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.domain.port.out.ActiveCartRepository;
import com.msd.smartcart.domain.port.out.CartBackupRepository;
import com.msd.smartcart.domain.port.out.OrderRepository;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.ProductDocument;
import com.msd.smartcart.shared.Result;
import com.msd.smartcart.shared.AppError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración end-to-end del flujo completo de checkout.
 * Levanta el contexto de Spring completo con MongoDB, Redis y RabbitMQ reales.
 * Verifica que los servicios de aplicación coordinan correctamente los adapters
 * de infraestructura en escenarios reales: carrito en Redis, productos en Mongo,
 * orden persistida, backup activado.
 * No mockea nada — cada dependencia es real.
 */
class CheckoutFlowIT extends IntegrationTestBase {

    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private ActiveCartRepository activeCartRepository;
    @Autowired private CartBackupRepository cartBackupRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private RedisTemplate<String, String> redisTemplate;

    private static final String USER_ID = "user-e2e-001";

    @BeforeEach
    void seedProducts() {
        // Inserta productos en MongoDB para que el CartService pueda encontrarlos
        mongoTemplate.save(ProductDocument.builder()
                .id("prod-laptop").name("Laptop Dell XPS 15").description("High-end laptop")
                .unitPrice(new BigDecimal("1299.99")).stock(10).build());

        mongoTemplate.save(ProductDocument.builder()
                .id("prod-mouse").name("Logitech MX Master").description("Wireless mouse")
                .unitPrice(new BigDecimal("79.99")).stock(5).build());

        mongoTemplate.save(ProductDocument.builder()
                .id("prod-nostock").name("Sold-out GPU").description("Out of stock GPU")
                .unitPrice(new BigDecimal("599.99")).stock(0).build());
    }

    @AfterEach
    void cleanUp() {
        // Redis
        var cartKeys = redisTemplate.keys("cart:active:*");
        if (!cartKeys.isEmpty()) redisTemplate.delete(cartKeys);

        // MongoDB
        mongoTemplate.getDb().getCollection("products").drop();
        mongoTemplate.getDb().getCollection("orders").drop();
        mongoTemplate.getDb().getCollection("saved_carts").drop();
    }

    // =========================================================================
    // Cart → add items
    // =========================================================================

    @Test
    void should_addItemToCart_when_productExistsInCatalog() {
        Result<Cart, AppError> result = cartService.addItem(USER_ID, "prod-laptop", 1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().items()).hasSize(1);
        assertThat(result.getValue().items().getFirst().name()).isEqualTo("Laptop Dell XPS 15");
    }

    @Test
    void should_persistCartInRedis_when_itemAdded() {
        cartService.addItem(USER_ID, "prod-laptop", 1);

        assertThat(activeCartRepository.findByUserId(USER_ID)).isPresent();
    }

    @Test
    void should_incrementQuantity_when_addingExistingProductToCart() {
        cartService.addItem(USER_ID, "prod-laptop", 1);
        cartService.addItem(USER_ID, "prod-laptop", 2);

        Cart cart = activeCartRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void should_returnFailure_when_productDoesNotExistInCatalog() {
        Result<Cart, AppError> result = cartService.addItem(USER_ID, "prod-nonexistent", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.product_not_found");
    }

    @Test
    void should_triggerMongoBackup_when_cartValueExceedsThreshold() {
        // Laptop $1299.99 × 1 = $1299.99 > $500 → dispara backup
        cartService.addItem(USER_ID, "prod-laptop", 1);

        assertThat(cartBackupRepository.findLatestByUserId(USER_ID)).isPresent();
    }

    // =========================================================================
    // Cart → remove items
    // =========================================================================

    @Test
    void should_removeItemFromCart_when_removeItemCalled() {
        cartService.addItem(USER_ID, "prod-laptop", 2);
        cartService.removeItem(USER_ID, "prod-laptop", 1);

        Cart cart = activeCartRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(cart.items().getFirst().quantity()).isEqualTo(1);
    }

    @Test
    void should_removeProductEntirely_when_quantityReachesZero() {
        cartService.addItem(USER_ID, "prod-mouse", 1);
        cartService.removeItem(USER_ID, "prod-mouse", 1);

        Cart cart = activeCartRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(cart.items()).isEmpty();
    }

    // =========================================================================
    // Checkout — happy path
    // =========================================================================

    @Test
    void should_confirmOrder_when_checkoutCalledWithValidCart() {
        cartService.addItem(USER_ID, "prod-laptop", 1);

        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.Confirmed.class);

        CheckoutResult.Confirmed confirmed = (CheckoutResult.Confirmed) result.getValue();
        assertThat(confirmed.order().userId()).isEqualTo(USER_ID);
        assertThat(confirmed.order().totalAmount())
                .isEqualByComparingTo(new BigDecimal("1299.99"));
    }

    @Test
    void should_persistOrderInMongo_when_checkoutConfirmed() {
        cartService.addItem(USER_ID, "prod-laptop", 1);
        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        CheckoutResult.Confirmed confirmed = (CheckoutResult.Confirmed) result.getValue();
        assertThat(orderRepository.findById(confirmed.order().orderId())).isPresent();
    }

    @Test
    void should_clearCartFromRedis_when_checkoutConfirmed() {
        cartService.addItem(USER_ID, "prod-laptop", 1);
        checkoutService.checkout(USER_ID);

        assertThat(activeCartRepository.findByUserId(USER_ID)).isEmpty();
    }

    @Test
    void should_confirmOrderWithMultipleItems_when_checkoutCalled() {
        cartService.addItem(USER_ID, "prod-laptop", 1);
        cartService.addItem(USER_ID, "prod-mouse", 2);

        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        assertThat(result.isSuccess()).isTrue();
        CheckoutResult.Confirmed confirmed = (CheckoutResult.Confirmed) result.getValue();
        assertThat(confirmed.order().items()).hasSize(2);
        // 1299.99 + (2 × 79.99) = 1459.97
        assertThat(confirmed.order().totalAmount())
                .isEqualByComparingTo(new BigDecimal("1459.97"));
    }

    // =========================================================================
    // Checkout — carrito vacío
    // =========================================================================

    @Test
    void should_returnFailure_when_checkoutCalledWithEmptyCart() {
        // Crea un carrito vacío directamente en Redis
        activeCartRepository.save(Cart.createFor(USER_ID));

        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.empty");
    }

    @Test
    void should_returnFailure_when_checkoutCalledWithNoCart() {
        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.not_found");
    }

    // =========================================================================
    // Checkout — stock insuficiente → auto-corrección
    // =========================================================================

    @Test
    void should_returnOutOfStock_and_removeItem_when_productHasNoStock() {
        // Agrega un producto sin stock y otro con stock
        cartService.addItem(USER_ID, "prod-mouse", 1);    // stock: 5 ✓
        // Añadimos el sin-stock directamente al carrito ya que addItem valida cantidad, no stock
        Cart current = activeCartRepository.findByUserId(USER_ID).orElseThrow();
        Cart withOutOfStock = current.add(
                new CartItem("prod-nostock", "Sold-out GPU", 1, new BigDecimal("599.99")));
        activeCartRepository.save(withOutOfStock);

        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        // El carrito tenía 2 items: uno sin stock se elimina → retorna OutOfStock
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.OutOfStock.class);

        CheckoutResult.OutOfStock outOfStock = (CheckoutResult.OutOfStock) result.getValue();
        assertThat(outOfStock.products()).hasSize(1);
        assertThat(outOfStock.products().getFirst().productId()).isEqualTo("prod-nostock");

        // El carrito debe haberse corregido — solo queda el mouse
        Cart corrected = activeCartRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(corrected.items()).hasSize(1);
        assertThat(corrected.items().getFirst().productId()).isEqualTo("prod-mouse");
    }

    @Test
    void should_returnEmptyCartError_when_allItemsRemovedDueToNoStock() {
        Cart emptyCartWithOutOfStock = Cart.createFor(USER_ID)
                .add(new CartItem("prod-nostock", "GPU", 1, new BigDecimal("599.99")));
        activeCartRepository.save(emptyCartWithOutOfStock);

        Result<CheckoutResult, AppError> result = checkoutService.checkout(USER_ID);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.empty");
    }

    // =========================================================================
    // Historial de órdenes
    // =========================================================================

    @Test
    void should_accumulate_multiple_orders_for_same_user() {
        // Primera orden
        cartService.addItem(USER_ID, "prod-mouse", 1);
        checkoutService.checkout(USER_ID);

        // Segunda orden
        cartService.addItem(USER_ID, "prod-mouse", 2);
        checkoutService.checkout(USER_ID);

        List<Order> orders = orderRepository.findAllByUserId(USER_ID);
        assertThat(orders).hasSize(2);
    }
}