package com.xt.xiaoxingxing.playground.features.elasticsearch.support;

import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleFilterRequest;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.xt.xiaoxingxing.playground.features.elasticsearch.constants.ElasticsearchConstants;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
     * 构建包含全文查询和结构化过滤的文章查询。
     *
     * <p>这里使用 Elasticsearch Java API Client 的编程式 DSL，最终会构造成类似下面的 JSON DSL：</p>
     *
     * <pre>{@code
     * {
     *   "track_total_hits": true,
     *   "query": {
     *     "bool": {
     *       "must": [
     *         {
     *           "multi_match": {
     *             "query": "旅行",
     *             "fields": ["title^3", "summary^2", "content"],
     *             "boost": 1.5
     *           }
     *         }
     *       ],
     *       "filter": [
     *         {"term": {"category": "旅行"}},
     *         {"terms": {"tags": ["江南", "小镇"]}},
     *         {"term": {"enabled": true}},
     *         {"range": {"publishedAt": {
     *           "gte": "2026-01-01T00:00:00Z",
     *           "lte": "2026-08-27T23:59:59Z"
     *         }}},
     *         {"range": {"viewCount": {
     *           "gte": 100,
     *           "lte": 1000
     *         }}}
     *       ],
     *       "should": [
     *         {"match": {"title": "江南"}},
     *         {"match": {"content": "雨天"}}
     *       ],
     *       "must_not": [
     *         {"term": {"difficulty": "ADVANCED"}}
     *       ],
     *       "minimum_should_match": 0
     *     }
     *   },
     *   "highlight": {
     *     "fields": {
     *       "title": {"number_of_fragments": 0},
     *       "summary": {"fragment_size": 180, "number_of_fragments": 2},
     *       "content": {"fragment_size": 180, "number_of_fragments": 3}
     *     }
     *   },
     *   "sort": [
     *     {"_score": "desc"},
     *     {"id": "asc"}
     *   ],
     *   "from": 0,
     *   "size": 10
     * }
     * }</pre>
     *
     * <p>向量检索使用单独的 KNN DSL，示例：</p>
     * <pre>{@code
     * {
     *   "knn": {
     *     "field": "embedding",
     *     "query_vector": [0.12, 0.18, 0.86, 0.78, 0.15, 0.11, 0.04, 0.03],
     *     "k": 5,
     *     "num_candidates": 50,
     *     "filter": [
     *       {"term": {"category": "旅行"}},
     *       {"term": {"enabled": true}}
     *     ],
     *     "boost": 1.0
     *   }
     * }
     * }</pre>
     *
     * <p>常见 Elasticsearch Search DSL 组件速查：</p>
     * <ul>
     *     <li>{@code query}：整个 Elasticsearch 查询对象。</li>
     *     <li>{@code bool}：组合多个查询条件的布尔查询容器。</li>
     *     <li>{@code must}：必须满足，并参与相关性评分；本文件将全文查询放在这里。</li>
     *     <li>{@code filter}：必须满足，但不参与相关性评分；本文件将分类、标签、状态和范围条件放在这里。</li>
     *     <li>{@code should}：应该满足；可用于“满足越多得分越高”，也可构造多个条件的“或者”关系。</li>
     *     <li>{@code must_not}：必须不满足，用于排除文档；例如排除某个分类。</li>
     *     <li>{@code minimum_should_match}：设置 {@code should} 至少满足几个条件。</li>
     *     <li>{@code match_all}：匹配全部文档；本文件在关键词为空时使用。</li>
     *     <li>{@code match}：对一个 text 字段进行分词后的全文检索；本文件没有直接使用。</li>
     *     <li>{@code multi_match}：在多个 text 字段中进行全文检索；本文件用于标题、摘要和正文。</li>
     *     <li>{@code query}（在全文查询中）：用户输入的搜索文字。</li>
     *     <li>{@code fields}：指定检索字段；{@code title^3} 表示标题权重为 3，{@code summary^2} 表示摘要权重为 2。</li>
     *     <li>{@code boost}：调整某个查询条件的评分权重；本文件支持给全文查询和 KNN 查询设置权重。</li>
     *     <li>{@code term}：对 keyword、boolean 等字段进行单值精确匹配；本文件用于分类、难度和启用状态。</li>
     *     <li>{@code terms}：对一个字段匹配多个精确值，多个值之间是“或者”关系；本文件用于多个标签。</li>
     *     <li>{@code range}：对日期或数字进行范围过滤；本文件用于发布时间和浏览量。</li>
     *     <li>{@code field}：指定当前查询针对哪个字段。</li>
     *     <li>{@code gte}：大于等于；{@code gt}：大于；{@code lte}：小于等于；{@code lt}：小于。</li>
     *     <li>{@code exists}：判断字段是否存在；本文件没有直接使用。</li>
     *     <li>{@code prefix}：匹配指定前缀；本文件没有直接使用。</li>
     *     <li>{@code wildcard}：使用通配符匹配；本文件没有直接使用。</li>
     *     <li>{@code regexp}：使用正则表达式匹配；本文件没有直接使用。</li>
     *     <li>{@code sort}：指定排序规则；本文件由 {@link #buildSort(String, String)} 构造。</li>
     *     <li>{@code _score}：全文或向量查询计算出的相关性分数。</li>
     *     <li>{@code from}：分页起始位置；通常和 {@code size} 一起使用。</li>
     *     <li>{@code size}：本次最多返回多少条文档。</li>
     *     <li>{@code track_total_hits}：是否精确统计命中总数；本文件的搜索请求在其他位置配置。</li>
     *     <li>{@code highlight}：配置命中关键词的高亮片段；本文件的搜索请求在其他位置配置。</li>
     *     <li>{@code knn}：近似最近邻向量检索；本文件通过 {@link #buildKnn(List, int, int, List, Float)} 构造。</li>
     * </ul>
     *
     * <p>当关键词为空时，全文查询部分使用 {@code match_all}，表示不限制文本内容，
     * 但仍然会继续执行分类、标签、状态等 {@code filter} 条件。</p>
     */
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
            filters.add(Query.of(query -> query.range(range -> range.number(number -> {
                number.field(ElasticsearchConstants.FIELD_VIEW_COUNT);
                if (request.getViewCountMin() != null) {
                    number.gte(request.getViewCountMin().doubleValue());
                }
                if (request.getViewCountMax() != null) {
                    number.lte(request.getViewCountMax().doubleValue());
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
