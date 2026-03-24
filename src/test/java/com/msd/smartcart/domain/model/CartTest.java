// src/test/java/com/msd/smartcart/domain/model/CartTest.java

package com.msd.smartcart.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CartTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Cart emptyCart() {
        return Cart.createFor("user-123");
    }

    private CartItem laptop() {
        return new CartItem("prod-001", "Laptop Dell XPS 15", 1, new BigDecimal("1299.99"));
    }

    private CartItem mouse() {
        return new CartItem("prod-002", "Mouse Logitech MX", 2, new BigDecimal("79.99"));
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void should_createCart_when_userIdProvided() {
        Cart cart = Cart.createFor("user-123");

        assertThat(cart.cartId()).isNotBlank();
        assertThat(cart.userId()).isEqualTo("user-123");
        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_addNewProduct_when_productNotInCart() {
        Cart cart = emptyCart().add(laptop());

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().productId()).isEqualTo("prod-001");
        assertThat(cart.items().getFirst().quantity()).isEqualTo(1);
    }

    @Test
    void should_incrementQuantity_when_addingExistingProduct() {
        Cart cart = emptyCart()
                .add(laptop())
                .add(new CartItem("prod-001", "Laptop Dell XPS 15", 2, new BigDecimal("1299.99")));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void should_reduceQuantity_when_removingPartialAmount() {
        Cart cart = emptyCart()
                .add(new CartItem("prod-001", "Laptop Dell XPS 15", 3, new BigDecimal("1299.99")))
                .remove("prod-001", 1);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void should_removeProduct_when_removingExactQuantity() {
        Cart cart = emptyCart()
                .add(laptop())
                .remove("prod-001", 1);

        assertThat(cart.items()).isEmpty();
    }

    @Test
    void should_removeProduct_when_removingMoreThanAvailable() {
        Cart cart = emptyCart()
                .add(laptop())
                .remove("prod-001", 99);

        assertThat(cart.items()).isEmpty();
    }

    @Test
    void should_doNothing_when_removingNonExistentProduct() {
        Cart cart = emptyCart()
                .add(laptop())
                .remove("prod-999", 1);

        assertThat(cart.items()).hasSize(1);
    }

    @Test
    void should_calculateTotalValue_when_cartHasMultipleItems() {
        Cart cart = emptyCart()
                .add(laptop())  // 1 x 1299.99 = 1299.99
                .add(mouse());  // 2 x 79.99   =  159.98

        assertThat(cart.totalValue()).isEqualByComparingTo(new BigDecimal("1459.97"));
    }

    @Test
    void should_keepOtherItems_when_addingNewProduct() {
        Cart cart = emptyCart()
                .add(laptop())
                .add(mouse());

        assertThat(cart.items()).hasSize(2);
    }

    @Test
    void should_removeProduct_when_quantityReachesZero() {
        Cart cart = emptyCart()
                .add(new CartItem("prod-001", "Laptop", 2, new BigDecimal("1299.99")))
                .remove("prod-001", 2);

        assertThat(cart.items()).isEmpty();
    }

    @Test
    void should_returnTrue_when_productExistsInCart() {
        Cart cart = emptyCart().add(laptop());
        assertThat(cart.containsProduct("prod-001")).isTrue();
    }

    @Test
    void should_returnFalse_when_productNotInCart() {
        Cart cart = emptyCart();
        assertThat(cart.containsProduct("prod-001")).isFalse();
    }

    @Test
    void should_keepOtherItemsIntact_when_incrementingExistingProduct() {
        Cart cart = emptyCart()
                .add(laptop())
                .add(mouse())
                .add(new CartItem("prod-001", "Laptop Dell XPS 15", 1, new BigDecimal("1299.99")));

        assertThat(cart.items()).hasSize(2);
        assertThat(cart.items().stream()
                .filter(i -> i.productId().equals("prod-001"))
                .findFirst().get().quantity()).isEqualTo(2);
        assertThat(cart.items().stream()
                .filter(i -> i.productId().equals("prod-002"))
                .findFirst().get().quantity()).isEqualTo(2);
    }
}