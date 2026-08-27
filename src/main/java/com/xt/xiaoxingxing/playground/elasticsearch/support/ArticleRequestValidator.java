package com.xt.xiaoxingxing.playground.elasticsearch.support;

import com.xt.xiaoxingxing.playground.elasticsearch.config.ElasticsearchProperties;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

/** Elasticsearch 特有的请求边界校验。 */
@Component
public class ArticleRequestValidator {

    private static final int MAX_RESULT_WINDOW = 10_000;

    private final ElasticsearchProperties properties;

    /** 注入 Elasticsearch 模块配置。 */
    public ArticleRequestValidator(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    /** 校验文章或查询向量的维度和数值。 */
    public void validateEmbedding(List<Float> embedding) {
        if (embedding == null) {
            throw new BusinessException("文章向量不能为空");
        }
        if (embedding.size() != properties.getVectorDimensions()) {
            throw new BusinessException("文章向量维度必须等于" + properties.getVectorDimensions());
        }
        double magnitudeSquared = 0D;
        for (int index = 0; index < embedding.size(); index++) {
            Float value = embedding.get(index);
            if (value == null) {
                throw new BusinessException("文章向量第" + (index + 1) + "个元素不能为空");
            }
            if (!Float.isFinite(value)) {
                throw new BusinessException("文章向量第" + (index + 1) + "个元素必须是有限数值");
            }
            magnitudeSquared += (double) value * value;
        }
        if (magnitudeSquared == 0D) {
            throw new BusinessException("cosine 相似度不支持零向量");
        }
    }

    /** 校验普通分页参数和结果窗口。 */
    public void validatePage(int pageNum, int pageSize) {
        if (pageNum <= 0) {
            throw new BusinessException("页码必须大于0");
        }
        if (pageSize <= 0) {
            throw new BusinessException("每页数量必须大于0");
        }
        if (pageSize > properties.getMaxPageSize()) {
            throw new BusinessException("每页数量不能超过" + properties.getMaxPageSize());
        }
        long from = (long) (pageNum - 1) * pageSize;
        if (from + pageSize > MAX_RESULT_WINDOW) {
            throw new BusinessException("分页结果窗口不能超过10000，请改用PIT和search_after");
        }
    }

    /** 校验单次 Bulk 写入数量。 */
    public void validateBulkSize(int bulkSize) {
        if (bulkSize <= 0) {
            throw new BusinessException("批量写入数量必须大于0");
        }
        if (bulkSize > properties.getMaxBulkSize()) {
            throw new BusinessException("批量写入数量不能超过" + properties.getMaxBulkSize());
        }
    }

    /** 校验 kNN 查询向量及候选数量。 */
    public void validateKnn(List<Float> queryVector, int k, int numCandidates) {
        validateEmbedding(queryVector);
        if (k <= 0) {
            throw new BusinessException("KNN 的k必须大于0");
        }
        if (k > MAX_RESULT_WINDOW) {
            throw new BusinessException("KNN 的k不能超过10000");
        }
        if (numCandidates > MAX_RESULT_WINDOW) {
            throw new BusinessException("KNN 的numCandidates不能超过10000");
        }
        if (numCandidates < k) {
            throw new BusinessException("KNN 的numCandidates必须大于等于k");
        }
    }
}
