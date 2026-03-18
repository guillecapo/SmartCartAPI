package com.msd.smartcart.infrastructure.adapter.out.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msd.smartcart.domain.model.OrderConfirmedEvent;
import com.msd.smartcart.domain.port.out.NotificationPublisher;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.FailedEventDocument;
import com.msd.smartcart.infrastructure.adapter.out.rabbitmq.message.OrderConfirmedMessage;
import com.msd.smartcart.shared.annotation.MessagingAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

@Slf4j
@MessagingAdapter
@RequiredArgsConstructor
public class NotificationPublisherAdapter implements NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    private static final String EXCHANGE = "smartcart.exchange";
    private static final String ROUTING_KEY = "order.confirmed";

    @Override
    @Retryable(
            retryFor = {AmqpException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
            // intento 1 → falla → espera 1s
            // intento 2 → falla → espera 2s
            // intento 3 → falla → @Recover
    )
    public void publish(OrderConfirmedEvent event) {
        OrderConfirmedMessage message = OrderConfirmedMessage.from(event);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
        log.info("Event published to RabbitMQ [eventId={}, orderId={}]",
                event.eventId(), event.orderId());
    }

    @Recover
    public void handlePublishFailure(AmqpException e, OrderConfirmedEvent event) {
        log.error("RabbitMQ publish failed after 3 attempts [eventId={}, orderId={}] — {}",
                event.eventId(), event.orderId(), e.getMessage(), e);

        OrderConfirmedMessage message = OrderConfirmedMessage.from(event);

        String payload = serializePayload(message);

        FailedEventDocument failedEvent = FailedEventDocument.from(
                message,
                e.getMessage(),
                payload
        );

        mongoTemplate.save(failedEvent);

        log.warn("Failed event persisted to MongoDB for later retry [eventId={}]",
                event.eventId());
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private String serializePayload(OrderConfirmedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize failed event payload [eventId={}]",
                    message.getEventId(), e);
            return "{}";
        }
    }
}