package com.xt.xiaoxingxing.playground.rocketmq.config;

/**
 * RocketMQ 学习模块的稳定名称约定。
 *
 * <p>这里只保留消息协议常量：事件类型、业务类型、操作类型和 JSON 结构版本。Topic、Tag、ConsumerGroup
 * 都是部署运行参数，统一由 {@link RocketMqLearningProperties} 从 YAML 提供，不能在此类写死。</p>
 */
public final class RocketMqNames {

    private RocketMqNames() {
    }

    /** 事件类型放在 JSON 信封内，消费者以它选择业务处理分支。 */
    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_ORDER_PAID = "ORDER_PAID";
    public static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String EVENT_OUTBOX_PAYMENT_TIMEOUT_CHECK = "OUTBOX_PAYMENT_TIMEOUT_CHECK";
    public static final String EVENT_TRANSACTION_PAYMENT_TIMEOUT_CHECK = "TRANSACTION_PAYMENT_TIMEOUT_CHECK";

    /** 通用事务记录中的业务类型；订单的稳定 businessKey 统一使用 orderNo。 */
    public static final String BUSINESS_ORDER = "ORDER";

    /** 事务记录和事务消息负载共用的订单操作协议值。 */
    public static final String OPERATION_CREATE = "CREATE";
    public static final String OPERATION_PAY = "PAY";
    public static final String OPERATION_CANCEL = "CANCEL";

    /**
     * 协议版本只描述消息 JSON 结构，和数据库乐观锁版本、订单状态流转版本完全无关。
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

}
