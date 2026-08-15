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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RocketMQ 可靠订单案例的全部运行配置。
 *
 * <p>Java 类只声明配置结构和合法性，不提供任何运行默认值。Topic、Tag、ConsumerGroup、订阅表达式和
 * 重试节奏都必须在 application YAML 中显式出现；这样阅读代码时不会同时寻找“Java 默认值”和
 * “YAML 覆盖值”两套来源。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "playground.rocketmq")
public class RocketMqLearningProperties {

    /** 关闭时不启动 RocketMQ 后台监听和调度任务。 */
    @NotNull
    private Boolean enabled;

    /** RocketMQ 5.x Client 连接 Proxy 的 gRPC endpoints。 */
    @NotBlank
    private String endpoints;

    /** 创建订单后等待多久执行付款超时检查。 */
    @Positive
    private long orderTimeoutMillis;

    /** PREPARED 事务在此保护窗口内由 Broker 回查时返回 UNKNOWN。 */
    @Positive
    private long transactionPreparedTimeoutSeconds;

    /** 每轮最多收口多少条过期 PREPARED 事务记录。 */
    @Positive
    @Max(1000)
    private int transactionCleanupBatchSize;

    @Valid
    @NotNull
    private Topics topics;

    @Valid
    @NotNull
    private Tags tags;

    @Valid
    @NotNull
    private ConsumerGroups consumerGroups;

    @Valid
    @NotNull
    private Subscriptions subscriptions;

    @Valid
    @NotNull
    private Consumer consumer;

    /** 两套订单链路共用的延迟消息边界配置。 */
    @Valid
    @NotNull
    private Delay delay;

    /** 商品库存 Cache Aside 查询使用的 Redis 配置。 */
    @Valid
    @NotNull
    private ProductCache productCache;

    @Valid
    @NotNull
    private TransactionCleanup transactionCleanup;

    @Valid
    @NotNull
    private Outbox outbox;

    /** 商品库存缓存只受扣库存和恢复库存影响；支付不改变商品库存，因此不订阅支付事件。 */
    @AssertTrue(message = "RocketMQ subscriptions.cache-events 必须精确包含 order-created、order-cancelled")
    public boolean isCacheEventsSubscriptionValid() {
        if (tags == null || subscriptions == null) {
            return true;
        }
        return subscriptionMatches(subscriptions.getCacheEvents(),
                tags.getOrderCreated(), tags.getOrderCancelled());
    }

    /** 统计组关注创建、支付、取消三种事实；它比只关心库存变化的缓存组多订阅支付事件。 */
    @AssertTrue(message = "RocketMQ subscriptions.statistics-events 必须精确包含 order-created、order-paid、order-cancelled")
    public boolean isStatisticsEventsSubscriptionValid() {
        if (tags == null || subscriptions == null) {
            return true;
        }
        return subscriptionMatches(subscriptions.getStatisticsEvents(),
                tags.getOrderCreated(), tags.getOrderPaid(), tags.getOrderCancelled());
    }

    /** 单个 Tag 不能伪装成组合订阅表达式，也不能含首尾空格。 */
    @AssertTrue(message = "RocketMQ 单个 Tag 不能重复、包含 || 或带首尾空白")
    public boolean isTagConfigurationValid() {
        if (tags == null) {
            return true;
        }
        String[] values = {
                tags.getOrderCreated(), tags.getOrderPaid(), tags.getOrderCancelled(),
                tags.getOutboxTimeout(), tags.getTransactionTimeout()
        };
        if (Arrays.stream(values).anyMatch(this::isMissing)) {
            return true;
        }
        boolean syntaxValid = Arrays.stream(values)
                .allMatch(tag -> tag.equals(tag.trim()) && !tag.contains("||"));
        return syntaxValid && new HashSet<>(Arrays.asList(values)).size() == values.length;
    }

    private boolean subscriptionMatches(String expression, String... expectedTags) {
        if (isMissing(expression) || Arrays.stream(expectedTags).anyMatch(this::isMissing)) {
            return true;
        }
        Set<String> actual = parseSubscription(expression);
        Set<String> expected = new HashSet<>(Arrays.asList(expectedTags));
        return actual != null && expected.size() == expectedTags.length && actual.equals(expected);
    }

    private Set<String> parseSubscription(String expression) {
        Set<String> tags = new LinkedHashSet<>();
        for (String token : expression.split("\\|\\|", -1)) {
            String tag = token.trim();
            if (tag.isEmpty() || !tags.add(tag)) {
                return null;
            }
        }
        return tags;
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class Topics {
        /** 普通订单事实事件 Topic。 */
        @NotBlank
        private String normal;
        /** 两套订单实现共用的延迟检查 Topic，通过 Tag 隔离。 */
        @NotBlank
        private String delay;
        /** RocketMQ 事务半消息 Topic。 */
        @NotBlank
        private String transaction;
    }

    @Data
    public static class Tags {
        @NotBlank
        private String orderCreated;
        @NotBlank
        private String orderPaid;
        @NotBlank
        private String orderCancelled;
        /** Outbox 下单流程的付款超时检查。 */
        @NotBlank
        private String outboxTimeout;
        /** RocketMQ 事务消息下单流程的付款超时检查。 */
        @NotBlank
        private String transactionTimeout;
    }

    @Data
    public static class ConsumerGroups {
        @NotBlank
        private String outboxOrderCache;
        @NotBlank
        private String outboxOrderStatistics;
        @NotBlank
        private String outboxOrderTimeout;
        @NotBlank
        private String transactionOrderCache;
        @NotBlank
        private String transactionOrderStatistics;
        @NotBlank
        private String transactionTimeoutScheduler;
        @NotBlank
        private String transactionOrderTimeout;
    }

    @Data
    public static class Subscriptions {
        /** ORDER_CREATED || ORDER_CANCELLED。 */
        @NotBlank
        private String cacheEvents;
        /** ORDER_CREATED || ORDER_PAID || ORDER_CANCELLED。 */
        @NotBlank
        private String statisticsEvents;
    }

    @Data
    public static class Consumer {
        @NotNull
        private Boolean sslEnabled;
        /** 官方 Starter 的单位为秒。 */
        @Positive
        private int requestTimeout;
        @Positive
        private int maxCachedMessageCount;
        @Positive
        private int maxCacheMessageSizeInBytes;
        @Positive
        private int consumptionThreadCount;
        @NotBlank
        @Pattern(regexp = "(?i)tag", message = "本案例只使用 Tag 订阅")
        private String filterExpressionType;
        /** 本地无 namespace/ACL 时也必须在 YAML 中显式配置为空字符串。 */
        @NotNull
        private String namespace;
        @NotNull
        private String accessKey;
        @NotNull
        private String secretKey;

        @AssertTrue(message = "RocketMQ consumer.access-key 与 secret-key 必须同时为空或同时配置")
        public boolean isCredentialPairValid() {
            if (accessKey == null || secretKey == null) {
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

    /**
     * DELAY Topic 的共享发送约束。
     *
     * <p>Outbox 延迟事件与事务消息超时中继都必须使用同一最小值，避免一条链路从配置读取、
     * 另一条链路在 Java 中写死 {@code 1} 秒，最终出现两套难以解释的行为。</p>
     */
    @Data
    public static class Delay {
        @Positive
        private long minimumBrokerDelaySeconds;
    }

    /** Cache Aside 商品库存查询的键空间和过期时间。 */
    @Data
    public static class ProductCache {
        /** 例如 playground:product:，最终键为前缀加 productId。 */
        @NotBlank
        private String keyPrefix;
        /** 缓存只用于查询加速，短 TTL 用于限制极端并发窗口下陈旧库存的存活时间。 */
        @Positive
        private long ttlSeconds;
    }

    /** Outbox 领取、租约、发布重试和退避配置。 */
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
        @Valid
        @NotNull
        private Retry retry;
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
