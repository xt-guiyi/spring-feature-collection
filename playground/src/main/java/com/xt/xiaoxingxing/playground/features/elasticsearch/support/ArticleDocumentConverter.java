package com.xt.xiaoxingxing.playground.features.elasticsearch.support;

import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleWriteRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.document.ArticleDocument;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleDetailResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleSearchHitResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ArticleDocumentConverter {

    /** 将写入请求转换为 Elasticsearch 文章文档。 */
    public ArticleDocument toDocument(String id, ArticleWriteRequest request) {
        return ArticleDocument.builder()
                .id(id)
                .title(request.getTitle())
                .summary(request.getSummary())
                .content(request.getContent())
                .category(request.getCategory())
                .tags(copyList(request.getTags()))
                .difficulty(request.getDifficulty())
                .publishedAt(request.getPublishedAt())
                .viewCount(request.getViewCount())
                .enabled(request.getEnabled())
                .titleSuggest(List.of(request.getTitle()))
                .embedding(copyList(request.getEmbedding()))
                .build();
    }

    /** 将文章文档转换为详情响应。 */
    public ArticleDetailResponse toDetail(ArticleDocument source) {
        ArticleDetailResponse target = new ArticleDetailResponse();
        copyDisplayFields(source, target);
        target.setTitleSuggest(copyList(source.getTitleSuggest()));
        target.setEmbedding(copyList(source.getEmbedding()));
        return target;
    }

    /** 将搜索命中文档转换为搜索结果响应。 */
    public ArticleSearchHitResponse toSearchHit(ArticleDocument source, Double score,
                                           Map<String, List<String>> highlights) {
        ArticleSearchHitResponse target = new ArticleSearchHitResponse();
        target.setId(source.getId());
        target.setTitle(source.getTitle());
        target.setSummary(source.getSummary());
        target.setContent(source.getContent());
        target.setCategory(source.getCategory());
        target.setTags(copyList(source.getTags()));
        target.setDifficulty(source.getDifficulty());
        target.setPublishedAt(source.getPublishedAt());
        target.setViewCount(source.getViewCount());
        target.setEnabled(source.getEnabled());
        target.setScore(score);
        target.setHighlights(copyHighlights(highlights));
        return target;
    }

    /** 复制文章详情使用的公共展示字段。 */
    private void copyDisplayFields(ArticleDocument source, ArticleDetailResponse target) {
        target.setId(source.getId());
        target.setTitle(source.getTitle());
        target.setSummary(source.getSummary());
        target.setContent(source.getContent());
        target.setCategory(source.getCategory());
        target.setTags(copyList(source.getTags()));
        target.setDifficulty(source.getDifficulty());
        target.setPublishedAt(source.getPublishedAt());
        target.setViewCount(source.getViewCount());
        target.setEnabled(source.getEnabled());
    }

    /** 安全复制列表并把空值转换为空列表。 */
    private <T> List<T> copyList(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    /** 安全复制搜索高亮结果。 */
    private Map<String, List<String>> copyHighlights(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> target = new LinkedHashMap<>();
        source.forEach((field, fragments) -> target.put(field, copyList(fragments)));
        return Collections.unmodifiableMap(target);
    }
}
