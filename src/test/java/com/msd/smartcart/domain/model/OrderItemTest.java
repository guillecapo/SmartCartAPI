package com.msd.smartcart.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void should_mapFromCartItem_when_fromCalled() {
        CartItem cartItem = new CartItem("prod-001", "Mouse", 3, new BigDecimal("79.99"));

        OrderItem orderItem = OrderItem.from(cartItem);

        assertThat(orderItem.productId()).isEqualTo("prod-001");
        assertThat(orderItem.productName()).isEqualTo("Mouse");
        assertThat(orderItem.quantity()).isEqualTo(3);
        assertThat(orderItem.unitPrice()).isEqualByComparingTo(new BigDecimal("79.99"));
        assertThat(orderItem.subtotal()).isEqualByComparingTo(new BigDecimal("239.97"));
    }

    @Test
    void should_calculateSubtotalAsUnitPriceTimesQuantity_when_fromCalled() {
        CartItem cartItem = new CartItem("prod-002", "Keyboard", 2, new BigDecimal("149.99"));

        OrderItem orderItem = OrderItem.from(cartItem);

        assertThat(orderItem.subtotal())
                .isEqualByComparingTo(new BigDecimal("299.98"));
    }
}