package com.msd.smartcart.domain.model;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItem from(CartItem item) {
        return new OrderItem(
                item.productId(),
                item.name(),
                item.quantity(),
                item.unitPrice(),
                item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
        );
    }
}