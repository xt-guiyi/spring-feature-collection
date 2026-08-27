package com.xt.xiaoxingxing.playground.elasticsearch.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import com.xt.xiaoxingxing.playground.elasticsearch.config.ElasticsearchConstants;
import com.xt.xiaoxingxing.playground.elasticsearch.document.ArticleDocument;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleWriteRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.service.ArticleCrudService;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleDocumentConverter;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleIndexGuard;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleRequestValidator;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleDetailVO;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

/** 使用 Elasticsearch Java API Client 完成文章检索投影的 CRUD。 */
@Service
public class JavaClientArticleCrudService implements ArticleCrudService {

    private final ElasticsearchClient client;
    private final ArticleDocumentConverter articleDocumentConverter;
    private final ArticleRequestValidator articleRequestValidator;
    private final ArticleIndexGuard articleIndexGuard;

    /** 注入 Java Client CRUD 所需的组件。 */
    public JavaClientArticleCrudService(ElasticsearchClient client,
                                        ArticleDocumentConverter articleDocumentConverter,
                                        ArticleRequestValidator articleRequestValidator,
                                        ArticleIndexGuard articleIndexGuard) {
        this.client = client;
        this.articleDocumentConverter = articleDocumentConverter;
        this.articleRequestValidator = articleRequestValidator;
        this.articleIndexGuard = articleIndexGuard;
    }

    /** 使用 Java Client 创建一篇文章。 */
    @Override
    public ArticleDetailVO create(ArticleWriteRequest request) {
        articleRequestValidator.validateEmbedding(request.getEmbedding());
        articleIndexGuard.requireAlias();
        ArticleDocument document = articleDocumentConverter.toDocument(UUID.randomUUID().toString(), request);
        index(document);
        return articleDocumentConverter.toDetail(document);
    }

    /** 使用 Java Client 按 ID 查询文章。 */
    @Override
    public ArticleDetailVO getById(String id) {
        articleIndexGuard.requireAlias();
        try {
            var response = client.get(request -> request
                    .index(ElasticsearchConstants.INDEX_ALIAS)
                    .id(id), ArticleDocument.class);
            if (!response.found()) {
                throw new BusinessException("文章不存在");
            }
            return articleDocumentConverter.toDetail(response.source());
        } catch (IOException exception) {
            throw new IllegalStateException("查询 Elasticsearch 文章失败", exception);
        }
    }

    /** 使用 Java Client 整篇替换指定文章。 */
    @Override
    public ArticleDetailVO update(String id, ArticleWriteRequest request) {
        articleIndexGuard.requireAlias();
        if (!exists(id)) {
            throw new BusinessException("文章不存在");
        }
        articleRequestValidator.validateEmbedding(request.getEmbedding());
        ArticleDocument document = articleDocumentConverter.toDocument(id, request);
        index(document);
        return articleDocumentConverter.toDetail(document);
    }

    /** 使用 Java Client 删除指定文章。 */
    @Override
    public boolean delete(String id) {
        articleIndexGuard.requireAlias();
        try {
            Result result = client.delete(request -> request
                    .index(ElasticsearchConstants.INDEX_ALIAS)
                    .id(id)
                    .refresh(Refresh.WaitFor))
                    .result();
            return result != Result.NotFound;
        } catch (IOException exception) {
            throw new IllegalStateException("删除 Elasticsearch 文章失败", exception);
        }
    }

    /** 判断指定 ID 的文章是否存在。 */
    private boolean exists(String id) {
        try {
            return client.exists(request -> request
                    .index(ElasticsearchConstants.INDEX_ALIAS)
                    .id(id))
                    .value();
        } catch (IOException exception) {
            throw new IllegalStateException("查询 Elasticsearch 文章是否存在失败", exception);
        }
    }

    /** 将文章写入 Elasticsearch 的 Alias。 */
    private void index(ArticleDocument document) {
        try {
            client.index(request -> request
                    .index(ElasticsearchConstants.INDEX_ALIAS)
                    .id(document.getId())
                    .document(document)
                    .requireAlias(true)
                    .refresh(Refresh.WaitFor));
        } catch (IOException exception) {
            throw new IllegalStateException("写入 Elasticsearch 文章失败", exception);
        }
    }
}
