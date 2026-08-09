package com.xt.xiaoxingxing.playground.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ 学习拓扑。
 *
 * <p>Exchange 决定“消息如何路由”，Queue 决定“消息保存在哪里”，Binding 则把两者连接起来。
 * 本类故意把 Classic、Quorum、Stream 三类声明放在一起，便于从代码和管理界面逐项对照。</p>
 */
@EnableRabbit
@EnableScheduling
@Configuration
@EnableConfigurationProperties(RabbitMqLearningProperties.class)
public class RabbitMqTopologyConfig {

    // ==================== 交换机 ====================

    @Bean
    public DirectExchange learningDirectExchange() {
        return new DirectExchange(RabbitMqNames.LEARNING_DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange learningTopicExchange() {
        return new TopicExchange(RabbitMqNames.LEARNING_TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange learningFanoutExchange() {
        return new FanoutExchange(RabbitMqNames.LEARNING_FANOUT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange learningDeadExchange() {
        return new DirectExchange(RabbitMqNames.LEARNING_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange learningRetryExchange() {
        return new DirectExchange(RabbitMqNames.LEARNING_RETRY_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(RabbitMqNames.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(RabbitMqNames.ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderRetryExchange() {
        return new DirectExchange(RabbitMqNames.ORDER_RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderDeadExchange() {
        return new DirectExchange(RabbitMqNames.ORDER_DEAD_EXCHANGE, true, false);
    }

    // ==================== Classic Queue：路由和 ACK 学习 ====================

    @Bean
    public Queue directEmailQueue() {
        return QueueBuilder.durable(RabbitMqNames.DIRECT_EMAIL_QUEUE).classic().build();
    }

    @Bean
    public Queue topicOrderQueue() {
        return QueueBuilder.durable(RabbitMqNames.TOPIC_ORDER_QUEUE).classic().build();
    }

    @Bean
    public Queue topicPaidQueue() {
        return QueueBuilder.durable(RabbitMqNames.TOPIC_PAID_QUEUE).classic().build();
    }

    @Bean
    public Queue fanoutQueueA() {
        return QueueBuilder.durable(RabbitMqNames.FANOUT_QUEUE_A).classic().build();
    }

    @Bean
    public Queue fanoutQueueB() {
        return QueueBuilder.durable(RabbitMqNames.FANOUT_QUEUE_B).classic().build();
    }

    @Bean
    public Queue ackDemoQueue() {
        return QueueBuilder.durable(RabbitMqNames.ACK_DEMO_QUEUE)
                .classic()
                .deadLetterExchange(RabbitMqNames.LEARNING_DEAD_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.ACK_DEMO_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue ackDemoRetryQueue(RabbitMqLearningProperties properties) {
        /*
         * 重试队列没有消费者。消息等待 TTL 后，通过默认交换机（空字符串）按“队列名 Routing Key”
         * 精确回到 ACK_DEMO_QUEUE。这样不会重新广播到其他已经成功的消费者。
         */
        return QueueBuilder.durable(RabbitMqNames.ACK_DEMO_RETRY_QUEUE)
                .classic()
                .ttl(Math.toIntExact(properties.getRetryDelayMillis()))
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", RabbitMqNames.ACK_DEMO_QUEUE)
                .build();
    }

    @Bean
    public Queue ackDemoDeadQueue() {
        return QueueBuilder.durable(RabbitMqNames.ACK_DEMO_DEAD_QUEUE).classic().build();
    }

    @Bean
    public Queue orderingDemoQueue() {
        return QueueBuilder.durable(RabbitMqNames.ORDERING_DEMO_QUEUE)
                .classic()
                // 集群中可以存在多个消费者，但同一时刻只有一个真正接收消息。
                .singleActiveConsumer()
                .build();
    }

    // ==================== Quorum Queue：可靠订单业务 ====================

    @Bean
    public Queue orderCacheQueue() {
        return quorumBusinessQueue(RabbitMqNames.ORDER_CACHE_QUEUE, RabbitMqNames.ORDER_CACHE_DEAD_KEY);
    }

    @Bean
    public Queue orderStatisticsQueue() {
        return quorumBusinessQueue(RabbitMqNames.ORDER_STATISTICS_QUEUE, RabbitMqNames.ORDER_STATISTICS_DEAD_KEY);
    }

    @Bean
    public Queue orderNotificationQueue() {
        return quorumBusinessQueue(RabbitMqNames.ORDER_NOTIFICATION_QUEUE, RabbitMqNames.ORDER_NOTIFICATION_DEAD_KEY);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return quorumBusinessQueue(RabbitMqNames.ORDER_TIMEOUT_QUEUE, RabbitMqNames.ORDER_TIMEOUT_DEAD_KEY);
    }

    @Bean
    public Queue orderTimeoutDelayQueue(RabbitMqLearningProperties properties) {
        return QueueBuilder.durable(RabbitMqNames.ORDER_TIMEOUT_DELAY_QUEUE)
                .quorum()
                .ttl(Math.toIntExact(properties.getOrderTimeoutMillis()))
                .deadLetterExchange(RabbitMqNames.ORDER_EVENT_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.ORDER_TIMEOUT_CHECK_KEY)
                .build();
    }

    @Bean
    public Queue orderCacheRetryQueue(RabbitMqLearningProperties properties) {
        return quorumRetryQueue(RabbitMqNames.ORDER_CACHE_RETRY_QUEUE,
                RabbitMqNames.ORDER_CACHE_QUEUE, properties.getRetryDelayMillis());
    }

    @Bean
    public Queue orderStatisticsRetryQueue(RabbitMqLearningProperties properties) {
        return quorumRetryQueue(RabbitMqNames.ORDER_STATISTICS_RETRY_QUEUE,
                RabbitMqNames.ORDER_STATISTICS_QUEUE, properties.getRetryDelayMillis());
    }

    @Bean
    public Queue orderNotificationRetryQueue(RabbitMqLearningProperties properties) {
        return quorumRetryQueue(RabbitMqNames.ORDER_NOTIFICATION_RETRY_QUEUE,
                RabbitMqNames.ORDER_NOTIFICATION_QUEUE, properties.getRetryDelayMillis());
    }

    @Bean
    public Queue orderTimeoutRetryQueue(RabbitMqLearningProperties properties) {
        return quorumRetryQueue(RabbitMqNames.ORDER_TIMEOUT_RETRY_QUEUE,
                RabbitMqNames.ORDER_TIMEOUT_QUEUE, properties.getRetryDelayMillis());
    }

    @Bean
    public Queue orderCacheDeadQueue() {
        return QueueBuilder.durable(RabbitMqNames.ORDER_CACHE_DEAD_QUEUE).quorum().build();
    }

    @Bean
    public Queue orderStatisticsDeadQueue() {
        return QueueBuilder.durable(RabbitMqNames.ORDER_STATISTICS_DEAD_QUEUE).quorum().build();
    }

    @Bean
    public Queue orderNotificationDeadQueue() {
        return QueueBuilder.durable(RabbitMqNames.ORDER_NOTIFICATION_DEAD_QUEUE).quorum().build();
    }

    @Bean
    public Queue orderTimeoutDeadQueue() {
        return QueueBuilder.durable(RabbitMqNames.ORDER_TIMEOUT_DEAD_QUEUE).quorum().build();
    }

    @Bean
    public Queue orderAuditStream() {
        // Stream 是追加日志，消费成功不会像普通队列那样删除消息，可通过 offset 重放。
        return QueueBuilder.durable(RabbitMqNames.ORDER_AUDIT_STREAM)
                .stream()
                .withArgument("x-max-age", "1D")
                .build();
    }

    // ==================== Binding：基础路由 ====================

    @Bean
    public Binding directEmailBinding(
            @Qualifier("directEmailQueue") Queue directEmailQueue,
            @Qualifier("learningDirectExchange") DirectExchange learningDirectExchange) {
        return BindingBuilder.bind(directEmailQueue).to(learningDirectExchange).with(RabbitMqNames.DIRECT_EMAIL_KEY);
    }

    @Bean
    public Binding topicOrderBinding(
            @Qualifier("topicOrderQueue") Queue topicOrderQueue,
            @Qualifier("learningTopicExchange") TopicExchange learningTopicExchange) {
        return BindingBuilder.bind(topicOrderQueue).to(learningTopicExchange).with(RabbitMqNames.TOPIC_ORDER_PATTERN);
    }

    @Bean
    public Binding topicPaidBinding(
            @Qualifier("topicPaidQueue") Queue topicPaidQueue,
            @Qualifier("learningTopicExchange") TopicExchange learningTopicExchange) {
        return BindingBuilder.bind(topicPaidQueue).to(learningTopicExchange).with(RabbitMqNames.TOPIC_PAID_PATTERN);
    }

    @Bean
    public Binding fanoutBindingA(
            @Qualifier("fanoutQueueA") Queue fanoutQueueA,
            @Qualifier("learningFanoutExchange") FanoutExchange learningFanoutExchange) {
        return BindingBuilder.bind(fanoutQueueA).to(learningFanoutExchange);
    }

    @Bean
    public Binding fanoutBindingB(
            @Qualifier("fanoutQueueB") Queue fanoutQueueB,
            @Qualifier("learningFanoutExchange") FanoutExchange learningFanoutExchange) {
        return BindingBuilder.bind(fanoutQueueB).to(learningFanoutExchange);
    }

    @Bean
    public Binding ackDemoBinding(
            @Qualifier("ackDemoQueue") Queue ackDemoQueue,
            @Qualifier("learningDirectExchange") DirectExchange learningDirectExchange) {
        return BindingBuilder.bind(ackDemoQueue).to(learningDirectExchange).with(RabbitMqNames.ACK_DEMO_KEY);
    }

    @Bean
    public Binding ackDemoRetryBinding(
            @Qualifier("ackDemoRetryQueue") Queue ackDemoRetryQueue,
            @Qualifier("learningRetryExchange") DirectExchange learningRetryExchange) {
        return BindingBuilder.bind(ackDemoRetryQueue).to(learningRetryExchange)
                .with(RabbitMqNames.ACK_DEMO_RETRY_KEY);
    }

    @Bean
    public Binding ackDemoDeadBinding(
            @Qualifier("ackDemoDeadQueue") Queue ackDemoDeadQueue,
            @Qualifier("learningDeadExchange") DirectExchange learningDeadExchange) {
        return BindingBuilder.bind(ackDemoDeadQueue).to(learningDeadExchange)
                .with(RabbitMqNames.ACK_DEMO_DEAD_KEY);
    }

    @Bean
    public Binding orderingDemoBinding(
            @Qualifier("orderingDemoQueue") Queue orderingDemoQueue,
            @Qualifier("learningDirectExchange") DirectExchange learningDirectExchange) {
        return BindingBuilder.bind(orderingDemoQueue).to(learningDirectExchange)
                .with(RabbitMqNames.ORDERING_DEMO_KEY);
    }

    // ==================== Binding：可靠订单、重试、死信和 Stream ====================

    @Bean
    public Binding orderCacheBinding(
            @Qualifier("orderCacheQueue") Queue orderCacheQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderCacheQueue).to(orderEventExchange).with(RabbitMqNames.ORDER_CREATED_KEY);
    }

    @Bean
    public Binding orderStatisticsBinding(
            @Qualifier("orderStatisticsQueue") Queue orderStatisticsQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderStatisticsQueue).to(orderEventExchange).with(RabbitMqNames.ORDER_ALL_PATTERN);
    }

    @Bean
    public Binding orderNotificationBinding(
            @Qualifier("orderNotificationQueue") Queue orderNotificationQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderNotificationQueue).to(orderEventExchange).with(RabbitMqNames.ORDER_CREATED_KEY);
    }

    @Bean
    public Binding orderTimeoutBinding(
            @Qualifier("orderTimeoutQueue") Queue orderTimeoutQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderTimeoutQueue).to(orderEventExchange)
                .with(RabbitMqNames.ORDER_TIMEOUT_CHECK_KEY);
    }

    @Bean
    public Binding orderTimeoutDelayBinding(
            @Qualifier("orderTimeoutDelayQueue") Queue orderTimeoutDelayQueue,
            @Qualifier("orderDelayExchange") DirectExchange orderDelayExchange) {
        return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderDelayExchange)
                .with(RabbitMqNames.ORDER_TIMEOUT_DELAY_KEY);
    }

    @Bean
    public Binding orderAuditStreamBinding(
            @Qualifier("orderAuditStream") Queue orderAuditStream,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderAuditStream).to(orderEventExchange).with(RabbitMqNames.ORDER_ALL_PATTERN);
    }

    @Bean
    public Binding orderCacheRetryBinding(
            @Qualifier("orderCacheRetryQueue") Queue orderCacheRetryQueue,
            @Qualifier("orderRetryExchange") DirectExchange orderRetryExchange) {
        return BindingBuilder.bind(orderCacheRetryQueue).to(orderRetryExchange)
                .with(RabbitMqNames.ORDER_CACHE_RETRY_KEY);
    }

    @Bean
    public Binding orderStatisticsRetryBinding(
            @Qualifier("orderStatisticsRetryQueue") Queue orderStatisticsRetryQueue,
            @Qualifier("orderRetryExchange") DirectExchange orderRetryExchange) {
        return BindingBuilder.bind(orderStatisticsRetryQueue).to(orderRetryExchange)
                .with(RabbitMqNames.ORDER_STATISTICS_RETRY_KEY);
    }

    @Bean
    public Binding orderNotificationRetryBinding(
            @Qualifier("orderNotificationRetryQueue") Queue orderNotificationRetryQueue,
            @Qualifier("orderRetryExchange") DirectExchange orderRetryExchange) {
        return BindingBuilder.bind(orderNotificationRetryQueue).to(orderRetryExchange)
                .with(RabbitMqNames.ORDER_NOTIFICATION_RETRY_KEY);
    }

    @Bean
    public Binding orderTimeoutRetryBinding(
            @Qualifier("orderTimeoutRetryQueue") Queue orderTimeoutRetryQueue,
            @Qualifier("orderRetryExchange") DirectExchange orderRetryExchange) {
        return BindingBuilder.bind(orderTimeoutRetryQueue).to(orderRetryExchange)
                .with(RabbitMqNames.ORDER_TIMEOUT_RETRY_KEY);
    }

    @Bean
    public Binding orderCacheDeadBinding(
            @Qualifier("orderCacheDeadQueue") Queue orderCacheDeadQueue,
            @Qualifier("orderDeadExchange") DirectExchange orderDeadExchange) {
        return BindingBuilder.bind(orderCacheDeadQueue).to(orderDeadExchange)
                .with(RabbitMqNames.ORDER_CACHE_DEAD_KEY);
    }

    @Bean
    public Binding orderStatisticsDeadBinding(
            @Qualifier("orderStatisticsDeadQueue") Queue orderStatisticsDeadQueue,
            @Qualifier("orderDeadExchange") DirectExchange orderDeadExchange) {
        return BindingBuilder.bind(orderStatisticsDeadQueue).to(orderDeadExchange)
                .with(RabbitMqNames.ORDER_STATISTICS_DEAD_KEY);
    }

    @Bean
    public Binding orderNotificationDeadBinding(
            @Qualifier("orderNotificationDeadQueue") Queue orderNotificationDeadQueue,
            @Qualifier("orderDeadExchange") DirectExchange orderDeadExchange) {
        return BindingBuilder.bind(orderNotificationDeadQueue).to(orderDeadExchange)
                .with(RabbitMqNames.ORDER_NOTIFICATION_DEAD_KEY);
    }

    @Bean
    public Binding orderTimeoutDeadBinding(
            @Qualifier("orderTimeoutDeadQueue") Queue orderTimeoutDeadQueue,
            @Qualifier("orderDeadExchange") DirectExchange orderDeadExchange) {
        return BindingBuilder.bind(orderTimeoutDeadQueue).to(orderDeadExchange)
                .with(RabbitMqNames.ORDER_TIMEOUT_DEAD_KEY);
    }

    // ==================== Listener Container ====================

    @Bean(name = "rabbitManualContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitManualContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean(name = "rabbitOrderedContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitOrderedContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 严格顺序案例一次只预取一条，且只创建一个消费者。
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    private Queue quorumBusinessQueue(String queueName, String deadRoutingKey) {
        return QueueBuilder.durable(queueName)
                .quorum()
                .deadLetterExchange(RabbitMqNames.ORDER_DEAD_EXCHANGE)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    private Queue quorumRetryQueue(String retryQueueName, String originalQueueName, long retryDelayMillis) {
        return QueueBuilder.durable(retryQueueName)
                .quorum()
                .ttl(Math.toIntExact(retryDelayMillis))
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", originalQueueName)
                .build();
    }
}
