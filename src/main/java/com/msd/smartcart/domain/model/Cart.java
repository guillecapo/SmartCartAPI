package com.msd.smartcart.domain.model;

import com.msd.smartcart.domain.model.enums.CartStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Cart(
        String cartId,
        String userId,
        List<CartItem> items,
        CartStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static Cart createFor(String userId) {
        return new Cart(
                UUID.randomUUID().toString(),
                userId,
                List.of(),
                CartStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
    }

    public BigDecimal totalValue() {
        return items.stream()
                .map(CartItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Cart add(CartItem item) {
        List<CartItem> updatedItems = new ArrayList<>(items);

        boolean exists = updatedItems.stream()
                .anyMatch(i -> i.productId().equals(item.productId()));

        if (exists) {
            updatedItems = updatedItems.stream()
                    .map(i -> i.productId().equals(item.productId())
                            ? i.withQuantity(i.quantity() + item.quantity())
                            : i)
                    .toList();
        } else {
            updatedItems.add(item);
        }

        return new Cart(cartId, userId, List.copyOf(updatedItems), status, createdAt, Instant.now());
    }

    public Cart remove(String productId, int quantity) {
        List<CartItem> updatedItems = items.stream()
                .map(i -> i.productId().equals(productId)
                        ? i.withQuantity(i.quantity() - quantity)
                        : i)
                .filter(i -> i.quantity() > 0)
                .toList();

        return new Cart(cartId, userId, List.copyOf(updatedItems), status, createdAt, Instant.now());
    }

    public boolean containsProduct(String productId) {
        return items.stream()
                .anyMatch(i -> i.productId().equals(productId));
    }
}