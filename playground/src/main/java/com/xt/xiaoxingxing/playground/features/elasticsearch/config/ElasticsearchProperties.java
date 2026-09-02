package com.xt.xiaoxingxing.playground.features.elasticsearch.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Elasticsearch 检索投影的可调参数。
 *
 * <p>配置前缀为 {@code playground.elasticsearch}，可以在 application.yml
 * 或 application.properties 中覆盖这里定义的默认值。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "playground.elasticsearch")
public class ElasticsearchProperties {

    /**
     * 文章向量的维度，必须与 Elasticsearch 向量字段的 {@code dims} 保持一致。
     * 当前项目固定使用 8 维向量。
     */
    @Min(value = 8, message = "当前固定文章向量维度必须为8")
    @Max(value = 8, message = "当前固定文章向量维度必须为8")
    private int vectorDimensions = 8;

    /** 普通分页查询允许的最大页大小，避免单次查询返回过多文档。 */
    @Min(1)
    private int maxPageSize = 50;

    /**
     * 一次 Bulk 批量写入允许处理的最大文档数量。
     * 最小值为 8，因为当前业务的一批固定文章数量为 8。
     */
    @Min(value = 8, message = "Bulk上限不能小于固定文章数量8")
    private int maxBulkSize = 100;

    /**
     * PIT（Point In Time）查询上下文的默认保留时间，用于深分页期间保持查询视图稳定。
     */
    @NotNull
    private Duration pitKeepAlive = Duration.ofMinutes(1);

    /** 向量检索默认返回的最相似文档数量，对应 KNN 查询中的 {@code k}。 */
    @Min(1)
    private int defaultK = 5;

    /**
     * KNN 检索默认筛选的候选文档数量，对应 {@code num_candidates}。
     * 通常应设置为不小于 {@link #defaultK}，候选数量越大结果通常越准确，但查询成本也越高。
     */
    @Min(1)
    private int defaultNumCandidates = 50;

    /** 创建 Elasticsearch 索引时使用的主分片数量。 */
    @Min(1)
    private int numberOfShards = 1;

    /** 创建 Elasticsearch 索引时使用的副本分片数量，开发环境通常设置为 0。 */
    @Min(0)
    private int numberOfReplicas = 0;

}
