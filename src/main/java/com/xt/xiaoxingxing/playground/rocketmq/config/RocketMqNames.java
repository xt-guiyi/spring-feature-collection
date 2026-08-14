package com.xt.xiaoxingxing.playground.rocketmq.config;

/**
 * RocketMQ 学习模块的稳定名称约定。
 *
 * <p>这里只保留消息协议常量：事件类型、JSON 结构版本和消息 Header。Topic、Tag、ConsumerGroup
 * 都是部署运行参数，统一由 {@link RocketMqLearningProperties} 从 YAML 提供，不能在此类写死。</p>
 */
public final class RocketMqNames {

    private RocketMqNames() {
    }

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

}
