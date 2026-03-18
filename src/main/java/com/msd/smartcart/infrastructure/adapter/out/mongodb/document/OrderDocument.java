package com.msd.smartcart.infrastructure.adapter.out.mongodb.document;

import com.msd.smartcart.domain.model.Order;
import com.msd.smartcart.domain.model.OrderItem;
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
@Document(collection = "orders")
public class OrderDocument {

    @Id
    private String orderId;
    private String cartId;
    private String userId;
    private List<OrderItemDocument> items;
    private BigDecimal totalAmount;
    private Instant confirmedAt;

    public static OrderDocument from(Order order) {
        return new OrderDocument(
                order.orderId(),
                order.cartId(),
                order.userId(),
                order.items().stream()
                        .map(OrderItemDocument::from)
                        .toList(),
                order.totalAmount(),
                order.confirmedAt()
        );
    }

    public Order toDomain() {
        return new Order(
                orderId,
                cartId,
                userId,
                items.stream()
                        .map(OrderItemDocument::toDomain)
                        .toList(),
                totalAmount,
                confirmedAt
        );
    }
}