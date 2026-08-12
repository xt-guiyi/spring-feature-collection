package com.xt.xiaoxingxing.playground.rocketmq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 学习案例的业务参数。
 *
 * <p>{@code rocketmq.producer.*} 由官方 Starter 管理客户端连接与发送行为；本类只承载订单超时、
 * Outbox 调度和事务回查等业务规则。两层配置分开，便于学习者区分“如何连 Broker”和“业务如何可靠处理”。</p>
 */
@Data
@ConfigurationProperties(prefix = "playground.rocketmq")
public class RocketMqLearningProperties {

    /** 5.x gRPC Client 应连接 Proxy 的 endpoints，不直接把 NameServer 暴露给应用。 */
    private String endpoints = "localhost:18081";

    /** 订单创建后多久发送延迟检查；最终是否取消仍由数据库条件更新决定。 */
    private long orderTimeoutMillis = 30 * 60 * 1000L;

    /**
     * PREPARED 事务记录在此窗口内可返回 UNKNOWN，避免过早回滚仍在执行的本地事务。
     *
     * <p>该值必须大于“正常本地事务的最坏执行时间”（包括数据库锁等待），否则 checker 或
     * 主动清理器可以合法先抢占 ROLLED_BACK，使本来只是较慢的订单事务整体回滚。</p>
     */
    private long transactionPreparedTimeoutSeconds = 120L;

    /**
     * 每轮主动收口最多扫描的过期 PREPARED 数量。
     *
     * <p>限制批量是为了避免历史堆积在一轮调度中长时间占用数据库；每条记录仍使用
     * 独立的条件更新裁决，它不是一个“批量强制回滚”开关。</p>
     */
    private int transactionCleanupBatchSize = 50;

    private Outbox outbox = new Outbox();

    /** Outbox 轮询、抢占和失败退避的参数，不是 RocketMQ 客户端连接参数。 */
    @Data
    public static class Outbox {
        private int batchSize = 20;
        private long fixedDelayMillis = 3_000L;
        private long lockTimeoutSeconds = 60L;
        private int maxPublishRetries = 10;
    }
}
