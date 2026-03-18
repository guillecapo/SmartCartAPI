package com.msd.smartcart.infrastructure.adapter.out.mongodb.document;

import com.msd.smartcart.domain.model.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDocument {

    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public static OrderItemDocument from(OrderItem item) {
        return new OrderItemDocument(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice(),
                item.subtotal()
        );
    }

    public OrderItem toDomain() {
        return new OrderItem(
                productId,
                productName,
                quantity,
                unitPrice,
                subtotal
        );
    }
}