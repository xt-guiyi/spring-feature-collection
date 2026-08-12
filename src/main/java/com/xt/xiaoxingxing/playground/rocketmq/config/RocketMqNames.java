package com.xt.xiaoxingxing.playground.rocketmq.config;

/**
 * RocketMQ 学习模块的稳定名称约定。
 *
 * <p>Topic、Tag 和 ConsumerGroup 都是 Broker 侧可见的契约，不能散落在生产者和消费者中手写。
 * 特别是 ConsumerGroup 保存独立消费进度：修改组名相当于让一个全新的消费者从新的进度开始消费，
 * 不是一次无害的重命名。</p>
 */
public final class RocketMqNames {

    private RocketMqNames() {
    }

    /** 四种 Topic 分开创建，消息类型不能在同一 Topic 内随意切换。 */
    public static final String NORMAL_TOPIC = "pg_learning_normal";
    public static final String FIFO_TOPIC = "pg_learning_fifo";
    public static final String DELAY_TOPIC = "pg_learning_delay";
    public static final String TRANSACTION_TOPIC = "pg_learning_transaction";

    /** Topic 内的二级分类；订阅者可用 Tag 表达式过滤，不应把它误解为一条独立队列。 */
    public static final String TAG_DEMO = "DEMO";
    public static final String TAG_RETRY = "RETRY_DEMO";
    public static final String TAG_ORDER_CREATED = "ORDER_CREATED";
    public static final String TAG_ORDER_PAID = "ORDER_PAID";
    public static final String TAG_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String TAG_ORDER_TIMEOUT = "ORDER_PAYMENT_TIMEOUT_CHECK";

    /** 事件类型放在 JSON 信封内，消费者以它选择业务处理分支。 */
    public static final String EVENT_DEMO_MESSAGE = "DEMO_MESSAGE";
    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_ORDER_PAID = "ORDER_PAID";
    public static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String EVENT_ORDER_PAYMENT_TIMEOUT_CHECK = "ORDER_PAYMENT_TIMEOUT_CHECK";
    public static final String EVENT_TRANSACTION_ORDER_CREATED = "TRANSACTION_ORDER_CREATED";

    /**
     * 协议版本只描述消息 JSON 结构，和数据库乐观锁版本、订单状态流转版本完全无关。
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 事务 ID 作为普通消息属性保存，Broker 回查时业务仍应以数据库记录为最终依据。 */
    public static final String HEADER_TRANSACTION_ID = "transactionId";

    // 以下组名是数据库幂等键的一部分，也是在 Dashboard 中观察消费进度的稳定标识。
    public static final String NORMAL_DEMO_GROUP = "pg_learning_normal_demo_group_v1";
    public static final String BROADCAST_AUDIT_GROUP = "pg_learning_broadcast_audit_group_v1";
    public static final String BROADCAST_NOTIFICATION_GROUP = "pg_learning_broadcast_notification_group_v1";
    public static final String FIFO_DEMO_GROUP = "pg_learning_fifo_demo_group_v1";
    public static final String DELAY_DEMO_GROUP = "pg_learning_delay_demo_group_v1";
    public static final String RETRY_DEMO_GROUP = "pg_learning_retry_demo_group_v1";
    public static final String ORDER_CACHE_GROUP = "pg_learning_order_cache_group_v1";
    public static final String ORDER_STATISTICS_GROUP = "pg_learning_order_statistics_group_v1";
    public static final String ORDER_NOTIFICATION_GROUP = "pg_learning_order_notification_group_v1";
    public static final String ORDER_TIMEOUT_GROUP = "pg_learning_order_timeout_group_v1";
    public static final String TRANSACTION_ORDER_GROUP = "pg_learning_transaction_order_group_v1";
}
