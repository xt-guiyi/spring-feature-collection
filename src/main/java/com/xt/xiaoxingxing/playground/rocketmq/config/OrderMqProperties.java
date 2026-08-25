package com.xt.xiaoxingxing.playground.rocketmq.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 订单消息业务配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "playground.order-mq")
public class OrderMqProperties {

    /** 订单支付超时时间。 */
    @Positive
    private long orderTimeoutMillis = 100_000L;

    /** 事务记录准备状态超时时间。 */
    @Positive
    private long transactionPreparedTimeoutSeconds = 120L;

    /** 每次清理的事务记录数量。 */
    @Positive
    @Max(1000)
    private int transactionCleanupBatchSize = 50;

    /** 商品缓存有效时间。 */
    @Positive
    private long productCacheTtlSeconds = 300L;
}
