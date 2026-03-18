package com.msd.smartcart.infrastructure.adapter.out.mongodb.document;

import com.msd.smartcart.infrastructure.adapter.out.rabbitmq.message.OrderConfirmedMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "failed_events")
public class FailedEventDocument {

    @Id
    private String eventId;
    private String orderId;
    private String userId;
    private String payload;        // JSON del mensaje completo
    private String errorMessage;
    private Instant failedAt;
    private int attemptCount;

    public static FailedEventDocument from(
            OrderConfirmedMessage message,
            String errorMessage,
            String payload
    ) {
        return new FailedEventDocument(
                message.getEventId(),
                message.getOrderId(),
                message.getUserId(),
                payload,
                errorMessage,
                Instant.now(),
                3
        );
    }
}