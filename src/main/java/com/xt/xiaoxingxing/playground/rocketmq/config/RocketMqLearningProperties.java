package com.xt.xiaoxingxing.playground.rocketmq.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RocketMQ 学习案例的业务参数。
 *
 * <p>本类是学习模块所有<strong>运行名称和运行参数</strong>的唯一入口：Topic、Tag、消费组、
 * 消费端并发参数、Outbox 轮询和事务清理节奏都必须在 YAML 中声明，不能再在 Java 中写默认值。
 * 这样切换环境时，可以一眼区分“代码协议”（事件类型、JSON 版本）与“部署参数”。</p>
 *
 * <p>注意：YAML 只能告诉客户端“使用哪个 Topic 名称”，不能创建或改变 Topic 的普通/顺序/延迟/
 * 事务类型；Topic 元数据仍由 Broker 管理。改动 {@link Topics} 名称时，必须同步 Docker 的 Topic
 * 初始化脚本。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "playground.rocketmq")
public class RocketMqLearningProperties {

    /** 只控制本模块后台 Listener 与调度器，不影响 Controller Bean 的注册。 */
    @NotNull
    private Boolean enabled;

    /** 5.x gRPC Client 应连接 Proxy 的 endpoints，不直接把 NameServer 暴露给应用。 */
    @NotBlank
    private String endpoints;

    /** 订单创建后多久发送延迟检查；最终是否取消仍由数据库条件更新决定。 */
    @Positive
    private long orderTimeoutMillis;

    /**
     * PREPARED 事务记录在此窗口内可返回 UNKNOWN，避免过早回滚仍在执行的本地事务。
     *
     * <p>该值必须大于“正常本地事务的最坏执行时间”（包括数据库锁等待），否则 checker 或
     * 主动清理器可以合法先抢占 ROLLED_BACK，使本来只是较慢的订单事务整体回滚。</p>
     */
    @Positive
    private long transactionPreparedTimeoutSeconds;

    /**
     * 每轮主动收口最多扫描的过期 PREPARED 数量。
     *
     * <p>限制批量是为了避免历史堆积在一轮调度中长时间占用数据库；每条记录仍使用
     * 独立的条件更新裁决，它不是一个“批量强制回滚”开关。</p>
     */
    @Positive
    @Max(1000)
    private int transactionCleanupBatchSize;

    /** Broker 可见的 Topic 名称；类型（普通/FIFO/延迟/事务）由 Broker 预先创建时决定。 */
    @Valid
    @NotNull
    private Topics topics = new Topics();

    /** Topic 内的二级路由标签；改名会影响 Producer 和 Listener 的订阅契约。 */
    @Valid
    @NotNull
    private Tags tags = new Tags();

    /** 每个监听器的稳定消费组名称；组名变更意味着新的消费进度和新的幂等维度。 */
    @Valid
    @NotNull
    private ConsumerGroups consumerGroups = new ConsumerGroups();

    /** 官方 v5 Listener 注解中的消费端运行参数，由 AnnotationEnhancer 写入注解属性。 */
    @Valid
    @NotNull
    private Consumer consumer = new Consumer();

    /** PREPARED 孤儿事务的主动扫描节奏。 */
    @Valid
    @NotNull
    private TransactionCleanup transactionCleanup = new TransactionCleanup();

    @Valid
    @NotNull
    private Outbox outbox = new Outbox();

    @Data
    public static class Topics {
        @NotBlank
        private String normal;
        @NotBlank
        private String fifo;
        @NotBlank
        private String delay;
        @NotBlank
        private String transaction;
    }

    @Data
    public static class Tags {
        @NotBlank
        private String demo;
        @NotBlank
        private String retry;
        @NotBlank
        private String orderCreated;
        @NotBlank
        private String orderPaid;
        @NotBlank
        private String orderCancelled;
        @NotBlank
        private String orderTimeout;
    }

    @Data
    public static class ConsumerGroups {
        @NotBlank
        private String normalDemo;
        @NotBlank
        private String broadcastAudit;
        @NotBlank
        private String broadcastNotification;
        @NotBlank
        private String fifoDemo;
        @NotBlank
        private String delayDemo;
        @NotBlank
        private String retryDemo;
        @NotBlank
        private String orderCache;
        @NotBlank
        private String orderStatistics;
        @NotBlank
        private String orderNotification;
        @NotBlank
        private String orderTimeout;
        @NotBlank
        private String transactionOrder;
    }

    @Data
    public static class Consumer {
        @NotNull
        private Boolean sslEnabled;
        /** 官方 Starter 的单位为秒，最终会构造成 {@code Duration.ofSeconds(requestTimeout)}。 */
        @Positive
        private int requestTimeout;
        @Positive
        private int maxCachedMessageCount;
        @Positive
        private int maxCacheMessageSizeInBytes;
        @Positive
        private int consumptionThreadCount;
        @NotBlank
        @Pattern(regexp = "(?i)tag", message = "本学习模块使用 Tag 订阅，filter-expression-type 只能配置为 tag")
        private String filterExpressionType;
        /** 无 namespace 的本地部署允许显式配置为空字符串。 */
        @NotNull
        private String namespace;
        /** 无 ACL 的本地部署允许显式配置为空字符串。 */
        @NotNull
        private String accessKey;
        /** 无 ACL 的本地部署允许显式配置为空字符串。 */
        @NotNull
        private String secretKey;

        /** RocketMQ 只有在 AK/SK 同时存在时才启用凭证，禁止只配一半后静默退化成无 ACL。 */
        @AssertTrue(message = "RocketMQ consumer.access-key 与 secret-key 必须同时为空或同时配置")
        public boolean isCredentialPairValid() {
            if (accessKey == null || secretKey == null) {
                // 缺失值由字段上的 @NotNull 给出更直接的配置键提示。
                return true;
            }
            return accessKey.isBlank() == secretKey.isBlank();
        }
    }

    @Data
    public static class TransactionCleanup {
        @Positive
        private long fixedDelayMillis;
        @Positive
        private long initialDelayMillis;
    }

    /** Outbox 轮询、抢占和失败退避的参数，不是 RocketMQ 客户端连接参数。 */
    @Data
    public static class Outbox {
        @Positive
        @Max(1000)
        private int batchSize;
        @Positive
        private long fixedDelayMillis;
        @Positive
        private long lockTimeoutSeconds;
        @Min(0)
        private int maxPublishRetries;
        /** 延迟消息到期后，仍至少交给 Broker 延迟多久，避免误走普通发送 API。 */
        @Positive
        private long minimumBrokerDelayMillis;
        @Valid
        @NotNull
        private Retry retry = new Retry();
    }

    @Data
    public static class Retry {
        @Positive
        private long initialDelaySeconds;
        @Positive
        private long maxDelaySeconds;
        @Min(0)
        @Max(30)
        private int maxExponent;
    }
}
