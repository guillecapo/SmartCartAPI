package com.msd.smartcart.domain.model;

import com.github.f4b6a3.ulid.UlidCreator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(
        String orderId,
        String cartId,
        String userId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        Instant confirmedAt
) {
    public static Order createFor(Cart cart) {
        return new Order(
                UlidCreator.getUlid().toString(),
                cart.cartId(),
                cart.userId(),
                cart.items().stream()
                        .map(OrderItem::from)
                        .toList(),
                cart.totalValue(),
                Instant.now()
        );
    }
}