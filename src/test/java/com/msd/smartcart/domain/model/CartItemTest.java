package com.msd.smartcart.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemTest {

    private CartItem laptop() {
        return new CartItem("prod-001", "Laptop Dell XPS 15", 2, new BigDecimal("1299.99"));
    }

    @Test
    void should_calculateSubtotal_when_quantityAndPriceProvided() {
        assertThat(laptop().subtotal())
                .isEqualByComparingTo(new BigDecimal("2599.98"));
    }

    @Test
    void should_returnNewItemWithUpdatedQuantity_when_withQuantityCalled() {
        CartItem updated = laptop().withQuantity(5);

        assertThat(updated.quantity()).isEqualTo(5);
        assertThat(updated.productId()).isEqualTo("prod-001");
        assertThat(updated.unitPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
    }

    @Test
    void should_notMutateOriginal_when_withQuantityCalled() {
        CartItem original = laptop();
        original.withQuantity(10);

        assertThat(original.quantity()).isEqualTo(2);
    }
}