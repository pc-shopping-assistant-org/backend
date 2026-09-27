package com.ecm.catalog.config;

import com.ecm.catalog.messaging.rabbitmq.RabbitTopology;
import com.ecm.catalog.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.ReserveStockCommand;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Declares the same topology as order-service (the producer) so declaration order at
 * startup doesn't matter — RabbitMQ declaration is idempotent. The listener container
 * factory rejects (instead of requeuing) a message whose listener throws, so it is
 * dead-lettered into {@code catalog.stock-commands.dlq} rather than looping forever.
 */
@Configuration
public class RabbitConfig {

    /**
     * order-service and catalog-service each declare their own copy of the command classes
     * (different packages), so the converter can't rely on the sender's fully-qualified
     * class name embedded in "__TypeId__" — both sides instead agree on a plain string id.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        var classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(Map.of(
                "ReserveStockCommand", ReserveStockCommand.class,
                "ReleaseStockCommand", ReleaseStockCommand.class));
        var converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
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
