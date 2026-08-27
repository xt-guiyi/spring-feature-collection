package com.xt.xiaoxingxing.playground.elasticsearch.support;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.xt.xiaoxingxing.playground.elasticsearch.config.ElasticsearchConstants;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleFilterRequest;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ArticleQueryFactory {

    /** 构建包含全文查询和结构化过滤的文章查询。 */
    public Query buildQuery(String keyword, ArticleFilterRequest request) {
        return buildTextQuery(keyword, buildFilters(request), null);
    }

    /** 构建可设置权重的全文查询。 */
    public Query buildTextQuery(String keyword, List<Query> filters, Float boost) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Query textQuery = normalizedKeyword == null
                ? Query.of(query -> query.matchAll(matchAll -> matchAll))
                : Query.of(query -> query.multiMatch(multiMatch -> {
                    multiMatch.query(normalizedKeyword)
                            .fields(ElasticsearchConstants.FIELD_TITLE + "^3",
                                    ElasticsearchConstants.FIELD_SUMMARY + "^2",
                                    ElasticsearchConstants.FIELD_CONTENT);
                    if (boost != null) {
                        multiMatch.boost(boost);
                    }
                    return multiMatch;
                }));
        if (filters.isEmpty()) {
            return textQuery;
        }
        return Query.of(query -> query.bool(bool -> bool.must(textQuery).filter(filters)));
    }

    /** 根据请求构建文章结构化过滤条件。 */
    public List<Query> buildFilters(ArticleFilterRequest request) {
        validateRanges(request);
        List<Query> filters = new ArrayList<>();

        String category = normalizeFilter(request.getCategory(), "文章分类");
        if (category != null) {
            filters.add(term(ElasticsearchConstants.FIELD_CATEGORY, category));
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            List<FieldValue> tags = request.getTags().stream()
                    .map(tag -> requireTag(tag).trim())
                    .map(FieldValue::of)
                    .toList();
            filters.add(Query.of(query -> query.terms(terms -> terms
                    .field(ElasticsearchConstants.FIELD_TAGS)
                    .terms(values -> values.value(tags)))));
        }

        String difficulty = normalizeFilter(request.getDifficulty(), "文章难度");
        if (difficulty != null) {
            filters.add(term(ElasticsearchConstants.FIELD_DIFFICULTY, difficulty));
        }
        if (request.getEnabled() != null) {
            filters.add(Query.of(query -> query.term(term -> term
                    .field(ElasticsearchConstants.FIELD_ENABLED)
                    .value(request.getEnabled()))));
        }
        if (request.getPublishedAtFrom() != null || request.getPublishedAtTo() != null) {
            filters.add(Query.of(query -> query.range(range -> range.date(date -> {
                date.field(ElasticsearchConstants.FIELD_PUBLISHED_AT);
                if (request.getPublishedAtFrom() != null) {
                    date.gte(request.getPublishedAtFrom().toString());
                }
                if (request.getPublishedAtTo() != null) {
                    date.lte(request.getPublishedAtTo().toString());
                }
                return date;
            }))));
        }
        if (request.getViewCountMin() != null || request.getViewCountMax() != null) {
            filters.add(Query.of(query -> query.range(range -> range.longNumber(number -> {
                number.field(ElasticsearchConstants.FIELD_VIEW_COUNT);
                if (request.getViewCountMin() != null) {
                    number.gte(request.getViewCountMin());
                }
                if (request.getViewCountMax() != null) {
                    number.lte(request.getViewCountMax());
                }
                return number;
            }))));
        }
        return List.copyOf(filters);
    }

    /** 构建 kNN 向量检索条件。 */
    public KnnSearch buildKnn(List<Float> queryVector, int k, int numCandidates,
                              List<Query> filters, Float boost) {
        KnnSearch.Builder builder = new KnnSearch.Builder()
                .field(ElasticsearchConstants.FIELD_EMBEDDING)
                .queryVector(queryVector)
                .k(k)
                .numCandidates(numCandidates);
        if (!filters.isEmpty()) {
            builder.filter(filters);
        }
        if (boost != null) {
            builder.boost(boost);
        }
        return builder.build();
    }

    /** 根据白名单字段构建文章排序条件。 */
    public List<SortOptions> buildSort(String sortBy, String sortOrder) {
        String normalizedField = requireValue(sortBy, "排序字段").toLowerCase(Locale.ROOT);
        String normalizedOrder = requireValue(sortOrder, "排序方向").toLowerCase(Locale.ROOT);
        SortOrder order = switch (normalizedOrder) {
            case "asc" -> SortOrder.Asc;
            case "desc" -> SortOrder.Desc;
            default -> throw new BusinessException("排序方向只支持asc或desc");
        };

        SortOptions primary = switch (normalizedField) {
            case "relevance" -> SortOptions.of(sort -> sort.score(score -> score.order(order)));
            case "title" -> fieldSort(ElasticsearchConstants.FIELD_TITLE + ".keyword", order);
            case "publishedat" -> fieldSort(ElasticsearchConstants.FIELD_PUBLISHED_AT, order);
            case "viewcount" -> fieldSort(ElasticsearchConstants.FIELD_VIEW_COUNT, order);
            default -> throw new BusinessException("排序字段只支持relevance、title、publishedAt或viewCount");
        };
        return List.of(primary, fieldSort(ElasticsearchConstants.FIELD_ID, SortOrder.Asc));
    }

    /** 构建单个字段排序条件。 */
    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(sort -> sort.field(value -> value.field(field).order(order)));
    }

    /** 构建 keyword 精确匹配条件。 */
    private Query term(String field, String value) {
        return Query.of(query -> query.term(term -> term.field(field).value(value)));
    }

    /** 校验时间和浏览量范围的起止关系。 */
    private void validateRanges(ArticleFilterRequest request) {
        if (request.getPublishedAtFrom() != null && request.getPublishedAtTo() != null
                && request.getPublishedAtFrom().isAfter(request.getPublishedAtTo())) {
            throw new BusinessException("发布时间起点不能晚于终点");
        }
        if (request.getViewCountMin() != null && request.getViewCountMax() != null
                && request.getViewCountMin() > request.getViewCountMax()) {
            throw new BusinessException("最小浏览量不能大于最大浏览量");
        }
    }

    /** 规范化可为空的全文检索关键词。 */
    private String normalizeKeyword(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 规范化可为空但不能为空白的过滤值。 */
    private String normalizeFilter(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new BusinessException(fieldName + "不能是空白值");
        }
        return value.trim();
    }

    /** 校验并返回非空白标签。 */
    private String requireTag(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("文章标签不能包含空白值");
        }
        return value;
    }

    /** 校验并返回非空白必填值。 */
    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
