package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response;

import lombok.Data;

import java.util.List;

/** Elasticsearch 文章索引与集群的当前状态。 */
@Data
public class ArticleIndexStatusResponse {

    private String alias;
    private List<String> indices;
    private String writeIndex;
    private long documentCount;
    private String clusterHealth;
}
