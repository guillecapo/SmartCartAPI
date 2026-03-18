package com.msd.smartcart.infrastructure.adapter.out.mongodb.document;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.domain.model.enums.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "saved_carts")
public class CartBackupDocument {

    @Id
    private String userId;
    private String cartId;
    private List<CartItem> items;
    private BigDecimal totalValue;
    private String itemsHash;
    private Instant savedAt;

    public static CartBackupDocument from(Cart cart, String itemsHash) {
        return new CartBackupDocument(
                cart.userId(),
                cart.cartId(),
                cart.items(),
                cart.totalValue(),
                itemsHash,
                Instant.now()
        );
    }

    public Cart toDomain() {
        return new Cart(
                cartId,
                userId,
                items,
                CartStatus.ACTIVE,
                savedAt,
                savedAt
        );
    }
}