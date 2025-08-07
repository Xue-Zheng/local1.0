package nz.etu.voting.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queue.email}")
    private String emailQueue;

    @Value("${app.rabbitmq.queue.sms}")
    private String smsQueue;

    @Value("${app.rabbitmq.queue.notification}")
    private String notificationQueue;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routingkey.email}")
    private String emailRoutingKey;

    @Value("${app.rabbitmq.routingkey.sms}")
    private String smsRoutingKey;

    @Value("${app.rabbitmq.queue.sync}")
    private String syncQueue;

    @Value("${app.rabbitmq.routingkey.sync}")
    private String syncRoutingKey;

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueue).build();
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(smsQueue).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(notificationQueue).build();
    }

    @Bean
    public Queue syncQueue() {
        return QueueBuilder.durable(syncQueue).build();
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(exchange()).with(emailRoutingKey);
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(smsQueue()).to(exchange()).with(smsRoutingKey);
    }

    @Bean
    public Binding syncBinding() {
        return BindingBuilder.bind(syncQueue()).to(exchange()).with(syncRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // 优化并发消费者配置，避免Mailjet速率限制
        factory.setConcurrentConsumers(5);  // 适度并发，避免过快
        factory.setMaxConcurrentConsumers(10); // 最大10个并发
        factory.setPrefetchCount(2); // 每个消费者预取2条消息，减少突发
        return factory;
    }
}