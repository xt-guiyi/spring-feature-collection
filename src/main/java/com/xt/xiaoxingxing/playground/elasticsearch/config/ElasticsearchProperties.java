package com.xt.xiaoxingxing.playground.elasticsearch.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Elasticsearch 检索投影的可调参数。 */
@Data
@Validated
@ConfigurationProperties(prefix = "playground.elasticsearch")
public class ElasticsearchProperties {

    @Min(value = 8, message = "当前固定文章向量维度必须为8")
    @Max(value = 8, message = "当前固定文章向量维度必须为8")
    private int vectorDimensions = 8;

    @Min(1)
    private int maxPageSize = 50;

    @Min(value = 8, message = "Bulk上限不能小于固定文章数量8")
    private int maxBulkSize = 100;

    @NotNull
    private Duration pitKeepAlive = Duration.ofMinutes(1);

    @Min(1)
    private int defaultK = 5;

    @Min(1)
    private int defaultNumCandidates = 50;

    @Min(1)
    private int numberOfShards = 1;

    @Min(0)
    private int numberOfReplicas = 0;

    /** PIT 必须有正的存活时间，避免深分页期间上下文立即失效。 */
    @AssertTrue(message = "Elasticsearch PIT保留时间必须大于0")
    public boolean isPitKeepAliveValid() {
        return pitKeepAlive == null || (!pitKeepAlive.isZero() && !pitKeepAlive.isNegative());
    }

    /** 校验默认 KNN 候选数量不小于返回数量。 */
    @AssertTrue(message = "Elasticsearch默认 num-candidates 不能小于 default-k")
    public boolean isDefaultKnnConfigurationValid() {
        return defaultNumCandidates >= defaultK;
    }
}
