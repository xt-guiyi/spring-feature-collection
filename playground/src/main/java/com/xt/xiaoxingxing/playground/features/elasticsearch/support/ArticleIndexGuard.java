package com.xt.xiaoxingxing.playground.features.elasticsearch.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.xt.xiaoxingxing.playground.features.elasticsearch.constants.ElasticsearchConstants;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 阻止未初始化时把稳定 Alias 名误创建成实体索引。 */
@Component
public class ArticleIndexGuard {

    private final ElasticsearchClient client;

    /** 注入检查 Alias 所需的 Elasticsearch Client。 */
    public ArticleIndexGuard(ElasticsearchClient client) {
        this.client = client;
    }

    /** 确认文章 Alias 已初始化。 */
    public void requireAlias() {
        try {
            boolean exists = client.indices().existsAlias(request -> request
                            .name(ElasticsearchConstants.INDEX_ALIAS))
                    .value();
            if (!exists) {
                throw new BusinessException("文章索引尚未初始化，请先调用 /api/playground/elasticsearch/index/initialize");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("检查 Elasticsearch 文章 Alias失败", exception);
        }
    }
}
