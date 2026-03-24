package com.msd.smartcart.infrastructure.adapter.out.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.FailedEventDocument;
import com.msd.smartcart.infrastructure.adapter.out.rabbitmq.message.OrderConfirmedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherAdapterTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private ObjectMapper objectMapper;

    private NotificationPublisherAdapter adapter;

    private static final String EXCHANGE = "smartcart.exchange";
    private static final String ROUTING_KEY = "order.confirmed";

    @BeforeEach
    void setUp() {
        adapter = new NotificationPublisherAdapter(rabbitTemplate, mongoTemplate, objectMapper);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private OrderConfirmedEvent event() {
        Cart cart = Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
        Order order = Order.createFor(cart);
        return OrderConfirmedEvent.from(order);
    }

    /**
     * Helper de stubbing: encapsula el checked exception de writeValueAsString.
     * El try/catch vive aquí — si el stub falla por razones inesperadas,
     * AssertionError detiene el test con un mensaje claro.
     */
    private void givenSerializationReturns(String json) {
        try {
            doReturn(json).when(objectMapper).writeValueAsString(any());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Unexpected stubbing failure in givenSerializationReturns", e);
        }
    }

    /**
     * Helper de stubbing: configura el objectMapper para lanzar JsonProcessingException.
     * Igual que arriba — el checked exception está contenido aquí, no en los tests.
     */
    private void givenSerializationFails() {
        try {
            doThrow(mock(JsonProcessingException.class)).when(objectMapper).writeValueAsString(any());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Unexpected stubbing failure in givenSerializationFails", e);
        }
    }

    // =========================================================================
    // publish — happy path
    // =========================================================================

    @Test
    void should_publishMessageToRabbitMQ_when_publishCalled() {
        adapter.publish(event());

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(OrderConfirmedMessage.class));
    }

    @Test
    void should_publishMessageWithCorrectEventData_when_publishCalled() {
        OrderConfirmedEvent e = event();

        adapter.publish(e);

        ArgumentCaptor<OrderConfirmedMessage> captor = ArgumentCaptor.forClass(OrderConfirmedMessage.class);
        verify(rabbitTemplate).convertAndSend(any(), any(), captor.capture());

        OrderConfirmedMessage sent = captor.getValue();
        assertThat(sent.getUserId()).isEqualTo("user-123");
        assertThat(sent.getOrderId()).isEqualTo(e.orderId());
        assertThat(sent.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1299.99"));
    }

    @Test
    void should_throwAmqpException_when_rabbitMQRejectsMessage() {
        doThrow(new AmqpException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        assertThatThrownBy(() -> adapter.publish(event()))
                .isInstanceOf(AmqpException.class);
    }

    // =========================================================================
    // handlePublishFailure — @Recover: serialización exitosa
    // =========================================================================

    @Test
    void should_persistFailedEventToMongo_when_recoverCalled() {
        givenSerializationReturns("{\"orderId\":\"abc\"}");
        AmqpException cause = new AmqpException("Broker down after 3 retries");

        adapter.handlePublishFailure(cause, event());

        verify(mongoTemplate).save(any(FailedEventDocument.class));
    }

    @Test
    void should_saveFailedEventWithCorrectData_when_recoverCalled() {
        givenSerializationReturns("{\"orderId\":\"abc\"}");
        OrderConfirmedEvent e = event();
        AmqpException cause = new AmqpException("Broker down");

        adapter.handlePublishFailure(cause, e);

        ArgumentCaptor<FailedEventDocument> captor = ArgumentCaptor.forClass(FailedEventDocument.class);
        verify(mongoTemplate).save(captor.capture());

        FailedEventDocument saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(e.orderId());
        assertThat(saved.getUserId()).isEqualTo("user-123");
        assertThat(saved.getPayload()).isEqualTo("{\"orderId\":\"abc\"}");
        assertThat(saved.getErrorMessage()).isEqualTo("Broker down");
        assertThat(saved.getAttemptCount()).isEqualTo(3);
    }

    // =========================================================================
    // handlePublishFailure — @Recover: fallo de serialización → payload vacío
    // =========================================================================

    @Test
    void should_persistFailedEventWithEmptyPayload_when_serializationFails() {
        givenSerializationFails();
        AmqpException cause = new AmqpException("Broker down");

        adapter.handlePublishFailure(cause, event());

        ArgumentCaptor<FailedEventDocument> captor = ArgumentCaptor.forClass(FailedEventDocument.class);
        verify(mongoTemplate).save(captor.capture());

        assertThat(captor.getValue().getPayload()).isEqualTo("{}");
    }
}