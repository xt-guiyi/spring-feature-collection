package com.xt.xiaoxingxing.playground.elasticsearch.service.impl;

import com.xt.xiaoxingxing.playground.elasticsearch.document.ArticleDocument;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleWriteRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.repository.ArticleRepository;
import com.xt.xiaoxingxing.playground.elasticsearch.service.ArticleCrudService;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleDocumentConverter;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleIndexGuard;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleRequestValidator;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleDetailVO;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 使用 Spring Data Elasticsearch Repository 完成文章检索投影的 CRUD。 */
@Service
public class SpringDataArticleCrudService implements ArticleCrudService {

    private final ArticleRepository articleRepository;
    private final ArticleDocumentConverter articleDocumentConverter;
    private final ArticleRequestValidator articleRequestValidator;
    private final ArticleIndexGuard articleIndexGuard;
    private final ElasticsearchOperations operations;

    /** 注入 Spring Data CRUD 所需的组件。 */
    public SpringDataArticleCrudService(ArticleRepository articleRepository,
                                        ArticleDocumentConverter articleDocumentConverter,
                                        ArticleRequestValidator articleRequestValidator,
                                        ArticleIndexGuard articleIndexGuard,
                                        ElasticsearchOperations operations) {
        this.articleRepository = articleRepository;
        this.articleDocumentConverter = articleDocumentConverter;
        this.articleRequestValidator = articleRequestValidator;
        this.articleIndexGuard = articleIndexGuard;
        this.operations = operations;
    }

    /** 使用 Spring Data 创建一篇文章。 */
    @Override
    public ArticleDetailVO create(ArticleWriteRequest request) {
        articleRequestValidator.validateEmbedding(request.getEmbedding());
        articleIndexGuard.requireAlias();
        ArticleDocument saved = articleRepository.save(
                articleDocumentConverter.toDocument(UUID.randomUUID().toString(), request));
        refreshForLocalSwaggerSearch();
        return articleDocumentConverter.toDetail(saved);
    }

    /** 使用 Spring Data 按 ID 查询文章。 */
    @Override
    public ArticleDetailVO getById(String id) {
        articleIndexGuard.requireAlias();
        return articleRepository.findById(id)
                .map(articleDocumentConverter::toDetail)
                .orElseThrow(() -> new BusinessException("文章不存在"));
    }

    /** 使用 Spring Data 整篇替换指定文章。 */
    @Override
    public ArticleDetailVO update(String id, ArticleWriteRequest request) {
        articleIndexGuard.requireAlias();
        if (!articleRepository.existsById(id)) {
            throw new BusinessException("文章不存在");
        }
        articleRequestValidator.validateEmbedding(request.getEmbedding());
        ArticleDocument saved = articleRepository.save(articleDocumentConverter.toDocument(id, request));
        refreshForLocalSwaggerSearch();
        return articleDocumentConverter.toDetail(saved);
    }

    /** 使用 Spring Data 删除指定文章。 */
    @Override
    public boolean delete(String id) {
        articleIndexGuard.requireAlias();
        if (!articleRepository.existsById(id)) {
            return false;
        }
        articleRepository.deleteById(id);
        refreshForLocalSwaggerSearch();
        return true;
    }

    /** 刷新文章索引，让本地 Swagger 查询立即看到写入结果。 */
    private void refreshForLocalSwaggerSearch() {
        // 仅为 Swagger 紧接写入后搜索的本地演示强制刷新；生产环境不应每次写入都刷新索引。
        operations.indexOps(ArticleDocument.class).refresh();
    }
}
