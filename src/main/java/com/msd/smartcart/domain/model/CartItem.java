package com.msd.smartcart.domain.model;

import java.math.BigDecimal;

public record CartItem(
        String productId,
        String name,
        int quantity,
        BigDecimal unitPrice
) {

    // Subtotal calculado — no se persiste, se deriva
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // Retorna nuevo CartItem con cantidad actualizada — inmutabilidad
    public CartItem withQuantity(int newQuantity) {
        return new CartItem(productId, name, newQuantity, unitPrice);
    }
}