package com.msd.smartcart.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConfirmedEventTest {

    private Order order() {
        Cart cart = Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
        return Order.createFor(cart);
    }

    @Test
    void should_createEventFromOrder_when_fromCalled() {
        Order o = order();

        OrderConfirmedEvent event = OrderConfirmedEvent.from(o);

        assertThat(event.orderId()).isEqualTo(o.orderId());
        assertThat(event.userId()).isEqualTo("user-123");
        assertThat(event.totalAmount()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(event.items()).hasSize(1);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void should_haveOwnEventId_distinct_from_orderId() {
        Order o = order();

        OrderConfirmedEvent event = OrderConfirmedEvent.from(o);

        // eventId es un ULID propio — no debe ser el mismo que el orderId
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.eventId()).isNotEqualTo(o.orderId());
    }

    @Test
    void should_generateUniqueEventIds_when_fromCalledTwice() {
        Order o = order();

        OrderConfirmedEvent e1 = OrderConfirmedEvent.from(o);
        OrderConfirmedEvent e2 = OrderConfirmedEvent.from(o);

        assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
}