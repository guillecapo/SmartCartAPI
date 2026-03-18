package com.msd.smartcart.infrastructure.adapter.out.rabbitmq.message;

import com.msd.smartcart.domain.model.OrderConfirmedEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedMessage {

    private String eventId;
    private String orderId;
    private String userId;
    private List<OrderItemMessage> items;
    private BigDecimal totalAmount;
    private Instant occurredAt;

    public static OrderConfirmedMessage from(OrderConfirmedEvent event) {
        return new OrderConfirmedMessage(
                event.eventId(),
                event.orderId(),
                event.userId(),
                event.items().stream()
                        .map(OrderItemMessage::from)
                        .toList(),
                event.totalAmount(),
                event.occurredAt()
        );
    }
}