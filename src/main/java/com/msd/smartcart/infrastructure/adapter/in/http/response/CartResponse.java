package com.msd.smartcart.infrastructure.adapter.in.http.response;

import com.msd.smartcart.domain.model.Cart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String cartId;
    private String userId;
    private List<CartItemResponse> items;
    private BigDecimal totalValue;
    private String status;
    private Instant updatedAt;

    public static CartResponse from(Cart cart) {
        return new CartResponse(
                cart.cartId(),
                cart.userId(),
                cart.items().stream()
                        .map(CartItemResponse::from)
                        .toList(),
                cart.totalValue(),
                cart.status().name(),
                cart.updatedAt()
        );
    }
}