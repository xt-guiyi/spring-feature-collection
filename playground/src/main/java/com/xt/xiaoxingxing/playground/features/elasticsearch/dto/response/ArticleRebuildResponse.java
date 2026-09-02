package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response;

import lombok.Data;

/** Alias 原子切换后的索引重建结果。 */
@Data
public class ArticleRebuildResponse {

    private String alias;
    private String sourceIndex;
    private String targetIndex;
    private long documentCount;
}
