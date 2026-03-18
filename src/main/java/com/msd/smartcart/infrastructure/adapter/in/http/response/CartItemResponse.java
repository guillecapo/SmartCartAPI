package com.msd.smartcart.infrastructure.adapter.in.http.response;

import com.msd.smartcart.domain.model.CartItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private String productId;
    private String name;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(
                item.productId(),
                item.name(),
                item.quantity(),
                item.unitPrice(),
                item.subtotal()
        );
    }
}