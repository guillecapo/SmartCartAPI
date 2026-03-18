package com.msd.smartcart.domain.model;

import com.github.f4b6a3.ulid.UlidCreator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderConfirmedEvent(
        String eventId,
        String orderId,
        String userId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        Instant occurredAt
) {
    public static OrderConfirmedEvent from(Order order) {
        return new OrderConfirmedEvent(
                UlidCreator.getUlid().toString(),  // eventId propio — no es el orderId
                order.orderId(),
                order.userId(),
                order.items(),
                order.totalAmount(),
                Instant.now()
        );
    }
}
