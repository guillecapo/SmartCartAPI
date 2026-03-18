package com.msd.smartcart.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@Configuration
public class RabbitMQConfig {

    // Exchange
    public static final String EXCHANGE = "smartcart.exchange";

    // Queues
    public static final String ORDER_CONFIRMED_QUEUE = "order.confirmed.queue";
    public static final String ORDER_CONFIRMED_DLQ = "order.confirmed.dlq";

    // Routing keys
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";
    public static final String ORDER_CONFIRMED_DLQ_ROUTING_KEY = "order.confirmed.dead";

    @Bean
    public DirectExchange smartCartExchange() {
        return new DirectExchange(EXCHANGE);
    }

    // Cola principal con DLQ configurada
    @Bean
    public Queue orderConfirmedQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMED_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_CONFIRMED_DLQ_ROUTING_KEY)
                .build();
    }

    // Dead Letter Queue — sin consumer activo
    @Bean
    public Queue orderConfirmedDlq() {
        return QueueBuilder.durable(ORDER_CONFIRMED_DLQ).build();
    }

    @Bean
    public Binding orderConfirmedBinding() {
        return BindingBuilder
                .bind(orderConfirmedQueue())
                .to(smartCartExchange())
                .with(ORDER_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public Binding orderConfirmedDlqBinding() {
        return BindingBuilder
                .bind(orderConfirmedDlq())
                .to(smartCartExchange())
                .with(ORDER_CONFIRMED_DLQ_ROUTING_KEY);
    }

    // Serialización JSON para mensajes
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}