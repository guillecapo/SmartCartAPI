package com.msd.smartcart.infrastructure.adapter.out.rabbitmq;

import com.msd.smartcart.IntegrationTestBase;
import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.FailedEventDocument;
import com.msd.smartcart.infrastructure.config.RabbitMQConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para NotificationPublisherAdapter contra RabbitMQ real.
 *
 * Nota: en lugar de consultar QUEUE_MESSAGE_COUNT (que tiene condiciones de carrera
 * porque RabbitMQ puede no haber procesado el mensaje aún), usamos
 * rabbitTemplate.receive() con timeout para consumir y verificar el mensaje.
 */
class NotificationPublisherIT extends IntegrationTestBase {

    @Autowired private NotificationPublisherAdapter adapter;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUp() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.ORDER_CONFIRMED_QUEUE);
        rabbitAdmin.purgeQueue(RabbitMQConfig.ORDER_CONFIRMED_DLQ);
        mongoTemplate.getDb().getCollection("failed_events").drop();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private OrderConfirmedEvent event() {
        Cart cart = Cart.createFor("user-rabbit-001")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
        Order order = Order.createFor(cart);
        return OrderConfirmedEvent.from(order);
    }

    // =========================================================================
    // publish — happy path
    // =========================================================================

    @Test
    void should_publishMessageToQueue_when_publishCalled() {
        adapter.publish(event());

        // Consumimos el mensaje con timeout — más confiable que QUEUE_MESSAGE_COUNT
        Message raw = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_QUEUE, 3000);
        assertThat(raw).isNotNull();
    }

    @Test
    void should_publishMessageWithCorrectContent_when_publishCalled() {
        OrderConfirmedEvent e = event();

        adapter.publish(e);

        Message raw = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_QUEUE, 3000);
        assertThat(raw).isNotNull();

        String body = new String(raw.getBody());
        assertThat(body).contains("user-rabbit-001");
        assertThat(body).contains("1299.99");
        assertThat(body).contains(e.orderId());
    }

    @Test
    void should_publishMultipleMessages_when_publishCalledMultipleTimes() {
        adapter.publish(event());
        adapter.publish(event());

        // Consumimos ambos mensajes con timeout
        Message first = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_QUEUE, 3000);
        Message second = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_QUEUE, 3000);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
    }

    // =========================================================================
    // routing — mensajes van a la cola correcta, DLQ vacía
    // =========================================================================

    @Test
    void should_routeToMainQueue_and_leaveDlqEmpty_when_published() {
        adapter.publish(event());

        // Mensaje llega a la cola principal
        Message msg = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_QUEUE, 3000);
        assertThat(msg).isNotNull();

        // DLQ permanece vacía
        Message dlqMsg = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_DLQ, 1000);
        assertThat(dlqMsg).isNull();
    }

    // =========================================================================
    // handlePublishFailure (@Recover) — persiste en MongoDB
    // =========================================================================

    @Test
    void should_persistFailedEventToMongo_when_recoverCalled() {
        OrderConfirmedEvent e = event();
        AmqpException cause = new AmqpException("Simulated broker failure after 3 retries");

        adapter.handlePublishFailure(cause, e);

        List<FailedEventDocument> failedEvents = mongoTemplate.findAll(
                FailedEventDocument.class, "failed_events");
        assertThat(failedEvents).hasSize(1);
        assertThat(failedEvents.getFirst().getOrderId()).isEqualTo(e.orderId());
        assertThat(failedEvents.getFirst().getUserId()).isEqualTo("user-rabbit-001");
        assertThat(failedEvents.getFirst().getErrorMessage()).isEqualTo(cause.getMessage());
        assertThat(failedEvents.getFirst().getAttemptCount()).isEqualTo(3);
        assertThat(failedEvents.getFirst().getPayload()).isNotBlank();
    }

    @Test
    void should_notPublishToQueue_when_recoverCalled() {
        adapter.handlePublishFailure(new AmqpException("Broker down"), event());

        // El @Recover no publica a RabbitMQ — solo persiste en Mongo
        Message msg = rabbitTemplate.receive(RabbitMQConfig.ORDER_CONFIRMED_QUEUE, 1000);
        assertThat(msg).isNull();
    }
}