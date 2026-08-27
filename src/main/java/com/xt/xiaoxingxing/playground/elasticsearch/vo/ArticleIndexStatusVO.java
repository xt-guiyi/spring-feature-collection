package com.xt.xiaoxingxing.playground.elasticsearch.vo;

import lombok.Data;

import java.util.List;

/** Elasticsearch 文章索引与集群的当前状态。 */
@Data
public class ArticleIndexStatusVO {

    private String alias;
    private List<String> indices;
    private String writeIndex;
    private long documentCount;
    private String clusterHealth;
}
