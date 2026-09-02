package com.xt.xiaoxingxing.playground.features.elasticsearch.service;

import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleWriteRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleDetailResponse;

/** 文章检索投影的单条 CRUD 语义，两套 Elasticsearch 客户端保持一致。 */
public interface ArticleCrudService {

    /** 创建文章检索投影。 */
    ArticleDetailResponse create(ArticleWriteRequest request);

    /** 按 ID 查询文章检索投影。 */
    ArticleDetailResponse getById(String id);

    /** 更新指定文章检索投影。 */
    ArticleDetailResponse update(String id, ArticleWriteRequest request);

    /** 删除指定文章检索投影。 */
    boolean delete(String id);
}
