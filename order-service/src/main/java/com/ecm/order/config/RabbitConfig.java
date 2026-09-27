package com.ecm.order.config;

import com.ecm.order.messaging.rabbitmq.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the same topology as catalog-service (the consumer) so a command published
 * here before catalog-service has started still lands in a real queue instead of being
 * dropped. RabbitMQ declaration is idempotent, so both services declaring it is safe.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange stockCommandsExchange() {
        return new DirectExchange(RabbitTopology.COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange stockCommandsDeadLetterExchange() {
        return new FanoutExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue stockCommandsQueue() {
        return QueueBuilder.durable(RabbitTopology.STOCK_COMMANDS_QUEUE)
                .withArgument("x-dead-letter-exchange", RabbitTopology.DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue stockCommandsDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.STOCK_COMMANDS_DLQ).build();
    }

    @Bean
    public Binding reserveStockBinding() {
        return BindingBuilder.bind(stockCommandsQueue())
                .to(stockCommandsExchange())
                .with(RabbitTopology.ROUTING_KEY_RESERVE);
    }

    @Bean
    public Binding releaseStockBinding() {
        return BindingBuilder.bind(stockCommandsQueue())
                .to(stockCommandsExchange())
                .with(RabbitTopology.ROUTING_KEY_RELEASE);
    }

    @Bean
    public Binding stockCommandsDeadLetterBinding() {
        return BindingBuilder.bind(stockCommandsDeadLetterQueue())
                .to(stockCommandsDeadLetterExchange());
    }
}
