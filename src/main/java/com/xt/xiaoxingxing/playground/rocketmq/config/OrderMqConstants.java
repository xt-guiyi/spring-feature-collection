package com.xt.xiaoxingxing.playground.rocketmq.config;

/** 订单消息主题、标签和消费组常量。 */
public final class OrderMqConstants {

    private OrderMqConstants() {
    }

    public static final String TOPIC_TRANSACTION = "order_transactions";
    public static final String TOPIC_DELAY = "order_delay";

    public static final String TAG_ORDER_CREATED = "order_created";
    public static final String TAG_ORDER_PAID = "order_paid";
    public static final String TAG_ORDER_CANCELLED = "order_cancelled";
    public static final String TAG_PAYMENT_TIMEOUT_CHECK = "payment_timeout_check";

    public static final String CONSUMER_GROUP_ORDER_CACHE = "order_cache_group";
    public static final String CONSUMER_GROUP_ORDER_STATISTICS = "order_statistics_group";
    public static final String CONSUMER_GROUP_TIMEOUT_SCHEDULER = "timeout_scheduler_group";
    public static final String CONSUMER_GROUP_ORDER_TIMEOUT = "order_timeout_group";

}
