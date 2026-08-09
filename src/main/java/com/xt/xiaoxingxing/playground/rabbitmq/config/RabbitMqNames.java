package com.xt.xiaoxingxing.playground.rabbitmq.config;

/**
 * RabbitMQ 学习模块的所有交换机、队列、Routing Key 和 Header 名称。
 *
 * <p>把名称集中在一个类中不是为了“少写几个字符串”，而是为了避免生产者、队列绑定和消费者分别手写名称后
 * 出现一个字符的偏差。RabbitMQ 对名称完全精确匹配，这类偏差通常不会编译报错，只会让消息无法路由。</p>
 */
public final class RabbitMqNames {

    private RabbitMqNames() {
    }

    // ==================== Classic Queue 基础学习拓扑 ====================

    public static final String LEARNING_DIRECT_EXCHANGE = "pg.learning.direct.exchange";
    public static final String LEARNING_TOPIC_EXCHANGE = "pg.learning.topic.exchange";
    public static final String LEARNING_FANOUT_EXCHANGE = "pg.learning.fanout.exchange";
    public static final String LEARNING_DEAD_EXCHANGE = "pg.learning.dead.exchange";
    public static final String LEARNING_RETRY_EXCHANGE = "pg.learning.retry.exchange";

    public static final String DIRECT_EMAIL_QUEUE = "pg.learning.direct.email.queue";
    public static final String TOPIC_ORDER_QUEUE = "pg.learning.topic.order.queue";
    public static final String TOPIC_PAID_QUEUE = "pg.learning.topic.paid.queue";
    public static final String FANOUT_QUEUE_A = "pg.learning.fanout.queue.a";
    public static final String FANOUT_QUEUE_B = "pg.learning.fanout.queue.b";
    public static final String ACK_DEMO_QUEUE = "pg.learning.ack.queue";
    public static final String ACK_DEMO_RETRY_QUEUE = "pg.learning.ack.retry.queue";
    public static final String ACK_DEMO_DEAD_QUEUE = "pg.learning.ack.dead.queue";
    public static final String ORDERING_DEMO_QUEUE = "pg.learning.ordering.queue";

    public static final String DIRECT_EMAIL_KEY = "demo.direct.email";
    public static final String TOPIC_ORDER_PATTERN = "demo.order.#";
    public static final String TOPIC_PAID_PATTERN = "demo.*.paid";
    public static final String ACK_DEMO_KEY = "demo.ack";
    public static final String ACK_DEMO_RETRY_KEY = "retry.demo.ack";
    public static final String ACK_DEMO_DEAD_KEY = "dead.demo.ack";
    public static final String ORDERING_DEMO_KEY = "demo.ordering";

    // ==================== Quorum Queue 可靠订单拓扑 ====================

    public static final String ORDER_EVENT_EXCHANGE = "pg.order.event.exchange";
    public static final String ORDER_DELAY_EXCHANGE = "pg.order.delay.exchange";
    public static final String ORDER_RETRY_EXCHANGE = "pg.order.retry.exchange";
    public static final String ORDER_DEAD_EXCHANGE = "pg.order.dead.exchange";

    public static final String ORDER_CACHE_QUEUE = "pg.order.cache.queue";
    public static final String ORDER_STATISTICS_QUEUE = "pg.order.statistics.queue";
    public static final String ORDER_NOTIFICATION_QUEUE = "pg.order.notification.queue";
    public static final String ORDER_TIMEOUT_QUEUE = "pg.order.timeout.queue";
    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "pg.order.timeout.delay.queue";

    public static final String ORDER_CACHE_RETRY_QUEUE = "pg.order.cache.retry.queue";
    public static final String ORDER_STATISTICS_RETRY_QUEUE = "pg.order.statistics.retry.queue";
    public static final String ORDER_NOTIFICATION_RETRY_QUEUE = "pg.order.notification.retry.queue";
    public static final String ORDER_TIMEOUT_RETRY_QUEUE = "pg.order.timeout.retry.queue";

    public static final String ORDER_CACHE_DEAD_QUEUE = "pg.order.cache.dead.queue";
    public static final String ORDER_STATISTICS_DEAD_QUEUE = "pg.order.statistics.dead.queue";
    public static final String ORDER_NOTIFICATION_DEAD_QUEUE = "pg.order.notification.dead.queue";
    public static final String ORDER_TIMEOUT_DEAD_QUEUE = "pg.order.timeout.dead.queue";

    public static final String ORDER_AUDIT_STREAM = "pg.order.audit.stream";

    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String ORDER_PAID_KEY = "order.paid";
    public static final String ORDER_CANCELLED_KEY = "order.cancelled";
    public static final String ORDER_ALL_PATTERN = "order.#";
    public static final String ORDER_TIMEOUT_DELAY_KEY = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_CHECK_KEY = "order.timeout.check";

    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_ORDER_PAID = "ORDER_PAID";
    public static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String EVENT_ORDER_PAYMENT_TIMEOUT_CHECK = "ORDER_PAYMENT_TIMEOUT_CHECK";
    public static final String EVENT_DEMO_MESSAGE = "DEMO_MESSAGE";
    public static final String EVENT_STREAM_DEMO = "STREAM_DEMO";

    public static final String ORDER_CACHE_RETRY_KEY = "retry.order.cache";
    public static final String ORDER_STATISTICS_RETRY_KEY = "retry.order.statistics";
    public static final String ORDER_NOTIFICATION_RETRY_KEY = "retry.order.notification";
    public static final String ORDER_TIMEOUT_RETRY_KEY = "retry.order.timeout";

    public static final String ORDER_CACHE_DEAD_KEY = "dead.order.cache";
    public static final String ORDER_STATISTICS_DEAD_KEY = "dead.order.statistics";
    public static final String ORDER_NOTIFICATION_DEAD_KEY = "dead.order.notification";
    public static final String ORDER_TIMEOUT_DEAD_KEY = "dead.order.timeout";

    // ==================== 消息协议元数据 ====================

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String HEADER_SCHEMA_VERSION = "x-schema-version";
    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final String HEADER_DEMO_ACTION = "x-demo-action";
    public static final String HEADER_DEMO_FAIL_TIMES = "x-demo-fail-times";

    // 消费者名称参与 PostgreSQL 唯一键，修改名称等价于创建一个全新的消费组。
    public static final String CACHE_CONSUMER = "order-cache-consumer-v1";
    public static final String STATISTICS_CONSUMER = "order-statistics-consumer-v1";
    public static final String NOTIFICATION_CONSUMER = "order-notification-consumer-v1";
    public static final String TIMEOUT_CONSUMER = "order-timeout-consumer-v1";
}
