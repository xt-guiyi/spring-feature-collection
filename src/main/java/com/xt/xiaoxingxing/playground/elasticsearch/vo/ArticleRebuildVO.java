package com.xt.xiaoxingxing.playground.elasticsearch.vo;

import lombok.Data;

/** Alias 原子切换后的索引重建结果。 */
@Data
public class ArticleRebuildVO {

    private String alias;
    private String sourceIndex;
    private String targetIndex;
    private long documentCount;
}
