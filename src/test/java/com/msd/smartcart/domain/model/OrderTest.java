package com.msd.smartcart.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private Cart cart() {
        return Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 2, new BigDecimal("1299.99")));
    }

    @Test
    void should_createOrderFromCart_when_createForCalled() {
        Cart c = cart();

        Order order = Order.createFor(c);

        assertThat(order.orderId()).isNotBlank();
        assertThat(order.userId()).isEqualTo("user-123");
        assertThat(order.cartId()).isEqualTo(c.cartId());
        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("2599.98"));
        assertThat(order.confirmedAt()).isNotNull();
    }

    @Test
    void should_mapAllCartItemsToOrderItems_when_createForCalled() {
        Order order = Order.createFor(cart());

        assertThat(order.items()).hasSize(1);
        OrderItem item = order.items().get(0);
        assertThat(item.productId()).isEqualTo("prod-001");
        assertThat(item.productName()).isEqualTo("Laptop");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("2599.98"));
    }

    @Test
    void should_generateUniqueOrderIds_when_createForCalledTwice() {
        Order o1 = Order.createFor(cart());
        Order o2 = Order.createFor(cart());

        assertThat(o1.orderId()).isNotEqualTo(o2.orderId());
    }
}