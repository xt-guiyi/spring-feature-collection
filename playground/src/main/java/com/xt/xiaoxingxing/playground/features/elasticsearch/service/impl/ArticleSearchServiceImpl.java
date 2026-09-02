package com.xt.xiaoxingxing.playground.features.elasticsearch.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StatsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.util.NamedValue;
import com.xt.xiaoxingxing.playground.features.elasticsearch.constants.ElasticsearchConstants;
import com.xt.xiaoxingxing.playground.features.elasticsearch.config.ElasticsearchProperties;
import com.xt.xiaoxingxing.playground.features.elasticsearch.document.ArticleDocument;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleAggregationRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleCursorRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleHybridRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleKnnRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleSearchRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleCursorTokenRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleAggregationResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleCursorPageResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleCursorTokenResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleSearchHitResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.service.ArticleSearchService;
import com.xt.xiaoxingxing.playground.features.elasticsearch.support.ArticleDocumentConverter;
import com.xt.xiaoxingxing.playground.features.elasticsearch.support.ArticleIndexGuard;
import com.xt.xiaoxingxing.playground.features.elasticsearch.support.ArticleQueryFactory;
import com.xt.xiaoxingxing.playground.features.elasticsearch.support.ArticleRequestValidator;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ArticleSearchServiceImpl implements ArticleSearchService {

    private static final String CATEGORY_AGG = "categories";
    private static final String TAG_AGG = "tags";
    private static final String DIFFICULTY_AGG = "difficulties";
    private static final String PUBLISHED_MONTH_AGG = "published_by_month";
    private static final String VIEW_COUNT_STATS_AGG = "view_count_stats";
    private static final String TITLE_SUGGESTION = "title_suggestions";
    private static final String CURSOR_DATE_FORMAT = "strict_date_optional_time";
    private static final String PIT_TIE_BREAKER_FIELD = "_shard_doc";
    private static final int MAX_AGGREGATION_BUCKETS = 100;
    private static final float DEFAULT_TEXT_BOOST = 0.7F;
    private static final float DEFAULT_VECTOR_BOOST = 0.3F;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")
            .withZone(ZoneOffset.UTC);

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ArticleRequestValidator requestValidator;
    private final ArticleQueryFactory queryFactory;
    private final ArticleDocumentConverter articleDocumentConverter;
    private final ArticleIndexGuard articleIndexGuard;

    /** 创建文章检索服务并注入检索所需组件。 */
    public ArticleSearchServiceImpl(ElasticsearchClient client,
                                    ElasticsearchProperties properties,
                                    ArticleRequestValidator requestValidator,
                                    ArticleQueryFactory queryFactory,
                                    ArticleDocumentConverter articleDocumentConverter,
                                    ArticleIndexGuard articleIndexGuard) {
        this.client = client;
        this.properties = properties;
        this.requestValidator = requestValidator;
        this.queryFactory = queryFactory;
        this.articleDocumentConverter = articleDocumentConverter;
        this.articleIndexGuard = articleIndexGuard;
    }

    /** 按关键词、过滤条件和排序规则分页检索文章。 */
    @Override
    public PageResult<ArticleSearchHitResponse> search(ArticleSearchRequest request) {
        int pageNum = requirePositive(request.getPageNum(), "页码");
        int pageSize = requirePositive(request.getPageSize(), "每页数量");
        requestValidator.validatePage(pageNum, pageSize);
        articleIndexGuard.requireAlias();

        SearchRequest searchRequest = indexedSearch()
                .from((pageNum - 1) * pageSize)
                .size(pageSize)
                .query(queryFactory.buildQuery(request.getKeyword(), request))
                .sort(queryFactory.buildSort(request.getSortBy(), request.getSortOrder()))
                .trackTotalHits(track -> track.enabled(true))
                .highlight(highlight -> highlight.fields(
                        NamedValue.of(ElasticsearchConstants.FIELD_TITLE,
                                HighlightField.of(field -> field.numberOfFragments(0))),
                        NamedValue.of(ElasticsearchConstants.FIELD_SUMMARY,
                                HighlightField.of(field -> field.fragmentSize(180).numberOfFragments(2))),
                        NamedValue.of(ElasticsearchConstants.FIELD_CONTENT,
                                HighlightField.of(field -> field.fragmentSize(180).numberOfFragments(3)))))
                .build();
        SearchResponse<ArticleDocument> response = executeSearch(searchRequest, "查询 Elasticsearch 文章失败");
        log.info("搜索结果: {} ", response);
        List<ArticleSearchHitResponse> hits = mapHits(response.hits().hits());
        return pageResult(hits, totalHits(response), pageNum, pageSize);
    }

    /** 统计文章分类、标签、难度、发布时间和浏览量。 */
    @Override
    public ArticleAggregationResponse aggregations(ArticleAggregationRequest request) {
        articleIndexGuard.requireAlias();
        SearchRequest searchRequest = indexedSearch()
                .size(0)
                .query(queryFactory.buildQuery(request.getKeyword(), request))
                .trackTotalHits(track -> track.enabled(true))
                .aggregations(CATEGORY_AGG, aggregation -> aggregation.terms(terms -> terms
                        .field(ElasticsearchConstants.FIELD_CATEGORY)
                        .size(MAX_AGGREGATION_BUCKETS)))
                .aggregations(TAG_AGG, aggregation -> aggregation.terms(terms -> terms
                        .field(ElasticsearchConstants.FIELD_TAGS)
                        .size(MAX_AGGREGATION_BUCKETS)))
                .aggregations(DIFFICULTY_AGG, aggregation -> aggregation.terms(terms -> terms
                        .field(ElasticsearchConstants.FIELD_DIFFICULTY)
                        .size(MAX_AGGREGATION_BUCKETS)))
                .aggregations(PUBLISHED_MONTH_AGG, aggregation -> aggregation.dateHistogram(histogram -> histogram
                        .field(ElasticsearchConstants.FIELD_PUBLISHED_AT)
                        .calendarInterval(CalendarInterval.Month)
                        .format("yyyy-MM")))
                .aggregations(VIEW_COUNT_STATS_AGG, aggregation -> aggregation.stats(stats -> stats
                        .field(ElasticsearchConstants.FIELD_VIEW_COUNT)))
                .build();
        SearchResponse<ArticleDocument> response = executeSearch(searchRequest, "聚合 Elasticsearch 文章失败");
        Map<String, Aggregate> aggregations = response.aggregations();

        return new ArticleAggregationResponse(
                totalHits(response),
                termBuckets(requireAggregate(aggregations, CATEGORY_AGG), CATEGORY_AGG),
                termBuckets(requireAggregate(aggregations, TAG_AGG), TAG_AGG),
                termBuckets(requireAggregate(aggregations, DIFFICULTY_AGG), DIFFICULTY_AGG),
                monthBuckets(requireAggregate(aggregations, PUBLISHED_MONTH_AGG)),
                viewCountStats(requireAggregate(aggregations, VIEW_COUNT_STATS_AGG)));
    }

    /**
     * 搜索标题中的联想内容并返回文章标题。
     *
     * <p>不同查询方式的适用场景：</p>
     * <ul>
     *     <li>{@code completion}：适合从开头补全。</li>
     *     <li>{@code match}：适合搜索分词后的词语。</li>
     *     <li>{@code wildcard}：适合搜索任意位置包含的内容，但性能成本较高。</li>
     *     <li>{@code search_as_you_type}：适合更完整的输入联想。</li>
     * </ul>
     *
     * <p>当前方法使用 {@code wildcard}，在 {@code title.keyword} 上构造
     * {@code *关键词*}，因此输入标题中间的内容也可以匹配。</p>
     */
    @Override
    public List<String> suggestions(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) {
            throw new BusinessException("补全前缀不能为空");
        }
        if (size <= 0 || size > 10) {
            throw new BusinessException("补全数量必须在1到10之间");
        }
        articleIndexGuard.requireAlias();

        SearchRequest searchRequest = indexedSearch()
                .size(size)
                .query(Query.of(query -> query.wildcard(wildcard -> wildcard
                        .field(ElasticsearchConstants.FIELD_TITLE + ".keyword")
                        .value("*" + prefix.trim() + "*")
                        .caseInsensitive(true))))
                .build();
        SearchResponse<ArticleDocument> response = executeSearch(searchRequest, "查询 Elasticsearch 标题补全失败");
        log.info("搜索结果： {}", response);
        Set<String> values = new LinkedHashSet<>();
        for (Hit<ArticleDocument> hit : response.hits().hits()) {
            ArticleDocument source = hit.source();
            if (source != null && source.getTitle() != null && !source.getTitle().isBlank()) {
                values.add(source.getTitle());
            }
            if (values.size() == size) {
                return List.copyOf(values);
            }
        }
        return List.copyOf(values);
    }

    /**
     * 使用 completion suggester 按标题开头返回文章补全建议。
     * completion 只能匹配输入前缀，不能匹配标题中间的内容；中间内容匹配由 wildcard 版本负责。
     */
    @Override
    public List<String> completionSuggestions(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) {
            throw new BusinessException("补全前缀不能为空");
        }
        if (size <= 0 || size > 10) {
            throw new BusinessException("补全数量必须在1到10之间");
        }
        articleIndexGuard.requireAlias();

        SearchRequest searchRequest = indexedSearch()
                .size(0)
                .suggest(suggest -> suggest.suggesters(TITLE_SUGGESTION, field -> field
                        .prefix(prefix.trim())
                        .completion(completion -> completion
                                .field(ElasticsearchConstants.FIELD_TITLE_SUGGEST)
                                .size(size)
                                .skipDuplicates(true))))
                .build();
        SearchResponse<ArticleDocument> response = executeSearch(
                searchRequest, "查询 Elasticsearch 标题 completion 补全失败");
        log.info("completion 补全结果：{}", response);

        List<Suggestion<ArticleDocument>> suggestions = response.suggest().getOrDefault(
                TITLE_SUGGESTION, List.of());
        Set<String> values = new LinkedHashSet<>();
        for (Suggestion<ArticleDocument> suggestion : suggestions) {
            if (!suggestion.isCompletion()) {
                continue;
            }
            for (CompletionSuggestOption<ArticleDocument> option : suggestion.completion().options()) {
                if (option.text() != null && !option.text().isBlank()) {
                    values.add(option.text());
                }
                if (values.size() == size) {
                    return List.copyOf(values);
                }
            }
        }
        return List.copyOf(values);
    }

    /** 使用 PIT 和 search_after 游标分页检索文章。 */
    @Override
    public ArticleCursorPageResponse cursor(ArticleCursorRequest request) {
        int pageSize = requirePositive(request.getPageSize(), "每页数量");
        requestValidator.validatePage(1, pageSize);
        Query query = queryFactory.buildQuery(request.getKeyword(), request);
        String queryFingerprint = cursorQueryFingerprint(request);
        validateCursor(request.getCursor(), queryFingerprint);
        articleIndexGuard.requireAlias();

        String pitId = request.getCursor() == null
                ? openPit()
                : request.getCursor().getPitId();
        SearchRequest.Builder builder = sourceFiltered(new SearchRequest.Builder())
                .pit(pit -> pit.id(pitId).keepAlive(pitKeepAlive()))
                .size(pageSize + 1)
                .query(query)
                .sort(sort -> sort.field(field -> field
                        .field(ElasticsearchConstants.FIELD_PUBLISHED_AT)
                        .order(SortOrder.Desc)
                        .format(CURSOR_DATE_FORMAT)))
                .sort(sort -> sort.field(field -> field
                        .field(ElasticsearchConstants.FIELD_ID)
                        .order(SortOrder.Asc)))
                .sort(sort -> sort.field(field -> field
                        .field(PIT_TIE_BREAKER_FIELD)
                        .order(SortOrder.Asc)));
        if (request.getCursor() != null) {
            builder.searchAfter(List.of(
                    FieldValue.of(request.getCursor().getPublishedAt().toString()),
                    FieldValue.of(request.getCursor().getId()),
                    FieldValue.of(request.getCursor().getShardDoc().longValue())));
        }

        SearchResponse<ArticleDocument> response = executeSearch(builder.build(), "游标查询 Elasticsearch 文章失败");
        String responsePitId = requireResponsePitId(response.pitId());
        List<Hit<ArticleDocument>> hits = response.hits().hits();
        boolean hasMore = hits.size() > pageSize;
        List<Hit<ArticleDocument>> visibleHits = hits.subList(0, Math.min(hits.size(), pageSize));
        List<ArticleSearchHitResponse> items = mapHits(visibleHits);

        if (!hasMore) {
            closePit(responsePitId);
            return new ArticleCursorPageResponse(items, null, false);
        }
        ArticleCursorTokenResponse nextCursor = cursorToken(
                responsePitId, visibleHits.getLast(), queryFingerprint);
        return new ArticleCursorPageResponse(items, nextCursor, true);
    }

    /** 按查询向量执行文章 KNN 检索。 */
    @Override
    public List<ArticleSearchHitResponse> knn(ArticleKnnRequest request) {
        int k = request.getK() == null ? properties.getDefaultK() : request.getK();
        int numCandidates = request.getNumCandidates() == null
                ? properties.getDefaultNumCandidates()
                : request.getNumCandidates();
        requestValidator.validateKnn(request.getQueryVector(), k, numCandidates);
        articleIndexGuard.requireAlias();
        List<Query> filters = queryFactory.buildFilters(request);
        KnnSearch knn = queryFactory.buildKnn(request.getQueryVector(), k, numCandidates, filters, null);

        SearchRequest searchRequest = indexedSearch()
                .size(k)
                .knn(knn)
                .build();
        SearchResponse<ArticleDocument> response = executeSearch(searchRequest, "KNN 查询 Elasticsearch 文章失败");
        return mapHits(response.hits().hits());
    }

    /** 融合文本相关性和向量相似度检索文章。 */
    @Override
    public List<ArticleSearchHitResponse> hybrid(ArticleHybridRequest request) {
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            throw new BusinessException("混合检索关键词不能为空");
        }
        int k = request.getK() == null ? properties.getDefaultK() : request.getK();
        int numCandidates = request.getNumCandidates() == null
                ? properties.getDefaultNumCandidates()
                : request.getNumCandidates();
        requestValidator.validateKnn(request.getQueryVector(), k, numCandidates);
        articleIndexGuard.requireAlias();
        float textBoost = resolveBoost(request.getTextBoost(), DEFAULT_TEXT_BOOST, "文本权重");
        float vectorBoost = resolveBoost(request.getVectorBoost(), DEFAULT_VECTOR_BOOST, "向量权重");
        List<Query> filters = queryFactory.buildFilters(request);

        SearchRequest searchRequest = indexedSearch()
                .size(k)
                .query(queryFactory.buildTextQuery(request.getKeyword(), filters, textBoost))
                .knn(queryFactory.buildKnn(request.getQueryVector(), k, numCandidates, filters, vectorBoost))
                .build();
        SearchResponse<ArticleDocument> response = executeSearch(
                searchRequest, "混合查询 Elasticsearch 文章失败");
        return mapHits(response.hits().hits());
    }

    /** 创建指向文章 Alias 的检索请求构建器。 */
    private SearchRequest.Builder indexedSearch() {
        return sourceFiltered(new SearchRequest.Builder().index(ElasticsearchConstants.INDEX_ALIAS));
    }

    /** 配置检索响应不返回文章向量字段。 */
    private SearchRequest.Builder sourceFiltered(SearchRequest.Builder builder) {
        return builder.source(source -> source.filter(filter -> filter
                .excludes(ElasticsearchConstants.FIELD_EMBEDDING)));
    }

    /** 执行文章检索并校验超时和失败分片。 */
    private SearchResponse<ArticleDocument> executeSearch(SearchRequest request, String message) {
        try {
            SearchResponse<ArticleDocument> response = client.search(request, ArticleDocument.class);
            if (Boolean.TRUE.equals(response.timedOut())) {
                throw new IllegalStateException(message + "：请求已超时，未返回完整结果");
            }
            if (response.shards().failed().intValue() > 0) {
                throw new IllegalStateException(message + "：存在失败分片，failed="
                        + response.shards().failed());
            }
            return response;
        } catch (IOException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    /** 打开文章检索使用的 PIT。 */
    private String openPit() {
        try {
            String pitId = client.openPointInTime(request -> request
                            .index(ElasticsearchConstants.INDEX_ALIAS)
                            .keepAlive(pitKeepAlive()))
                    .id();
            if (pitId == null || pitId.isBlank()) {
                throw new IllegalStateException("Elasticsearch 打开PIT后未返回ID");
            }
            return pitId;
        } catch (IOException exception) {
            throw new IllegalStateException("打开 Elasticsearch PIT失败", exception);
        }
    }

    /** 关闭指定的文章检索 PIT。 */
    private void closePit(String pitId) {
        try {
            boolean succeeded = client.closePointInTime(request -> request.id(pitId)).succeeded();
            if (!succeeded) {
                throw new IllegalStateException("Elasticsearch PIT关闭失败");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("关闭 Elasticsearch PIT失败", exception);
        }
    }

    /** 将配置的 PIT 保活时间转换为客户端时间参数。 */
    private Time pitKeepAlive() {
        long milliseconds = Math.max(1L, properties.getPitKeepAlive().toMillis());
        return Time.of(time -> time.time(milliseconds + "ms"));
    }

    /** 校验并返回 Elasticsearch 响应中的 PIT 标识。 */
    private String requireResponsePitId(String pitId) {
        if (pitId == null || pitId.isBlank()) {
            throw new IllegalStateException("Elasticsearch PIT查询未返回新的pitId");
        }
        return pitId;
    }

    /** 校验续页游标及其查询指纹。 */
    private void validateCursor(ArticleCursorTokenRequest cursor, String queryFingerprint) {
        if (cursor == null) {
            return;
        }
        if (cursor.getPitId() == null || cursor.getPitId().isBlank()) {
            throw new BusinessException("PIT游标不能为空");
        }
        if (cursor.getPublishedAt() == null) {
            throw new BusinessException("游标发布时间不能为空");
        }
        if (cursor.getId() == null || cursor.getId().isBlank()) {
            throw new BusinessException("游标文章ID不能为空");
        }
        if (cursor.getShardDoc() == null) {
            throw new BusinessException("游标分片排序值不能为空");
        }
        if (cursor.getQueryFingerprint() == null || cursor.getQueryFingerprint().isBlank()) {
            throw new BusinessException("游标查询指纹不能为空");
        }
        if (!cursor.getQueryFingerprint().equals(queryFingerprint)) {
            throw new BusinessException("PIT续页时不能修改关键词或过滤条件");
        }
    }

    /** 根据最后一条命中的排序值生成下一页游标。 */
    private ArticleCursorTokenResponse cursorToken(String pitId, Hit<ArticleDocument> hit,
                                           String queryFingerprint) {
        if (hit.sort().size() < 3) {
            throw new IllegalStateException("Elasticsearch 游标命中缺少排序值");
        }
        return new ArticleCursorTokenResponse(
                pitId,
                sortPublishedAt(hit.sort().get(0)),
                sortText(hit.sort().get(1), "文章ID"),
                sortLong(hit.sort().get(2), "_shard_doc"),
                queryFingerprint);
    }

    /** 将发布时间排序值转换为时间对象。 */
    private Instant sortPublishedAt(FieldValue value) {
        if (value.isLong()) {
            return Instant.ofEpochMilli(value.longValue());
        }
        if (value.isDouble()) {
            return Instant.ofEpochMilli((long) value.doubleValue());
        }
        if (!value.isString()) {
            throw new IllegalStateException("Elasticsearch 游标发布时间排序值类型不正确");
        }
        try {
            return Instant.parse(value.stringValue());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("Elasticsearch 游标发布时间排序值无法解析", exception);
        }
    }

    /** 读取并校验字符串类型的排序值。 */
    private String sortText(FieldValue value, String fieldName) {
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new IllegalStateException("Elasticsearch 游标" + fieldName + "排序值类型不正确");
        }
        return value.stringValue();
    }

    /** 读取并校验长整型排序值。 */
    private long sortLong(FieldValue value, String fieldName) {
        if (!value.isLong()) {
            throw new IllegalStateException("Elasticsearch 游标" + fieldName + "排序值类型不正确");
        }
        return value.longValue();
    }

    /** 根据游标查询条件生成稳定指纹。 */
    private String cursorQueryFingerprint(ArticleCursorRequest request) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintPart(canonical, normalizeFingerprintValue(request.getKeyword()));
        appendFingerprintPart(canonical, normalizeFingerprintValue(request.getCategory()));
        List<String> tags = request.getTags() == null
                ? List.of()
                : request.getTags().stream().map(String::trim).sorted().toList();
        tags.forEach(tag -> appendFingerprintPart(canonical, tag));
        appendFingerprintPart(canonical, normalizeFingerprintValue(request.getDifficulty()));
        appendFingerprintPart(canonical, String.valueOf(request.getEnabled()));
        appendFingerprintPart(canonical, String.valueOf(request.getPublishedAtFrom()));
        appendFingerprintPart(canonical, String.valueOf(request.getPublishedAtTo()));
        appendFingerprintPart(canonical, String.valueOf(request.getViewCountMin()));
        appendFingerprintPart(canonical, String.valueOf(request.getViewCountMax()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK不支持SHA-256", exception);
        }
    }

    /** 按长度前缀格式追加一个查询指纹字段。 */
    private void appendFingerprintPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('|');
    }

    /** 规范化参与查询指纹计算的文本值。 */
    private String normalizeFingerprintValue(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    /** 将多条 Elasticsearch 命中转换为文章检索结果。 */
    private List<ArticleSearchHitResponse> mapHits(List<Hit<ArticleDocument>> hits) {
        return hits.stream().map(this::mapHit).toList();
    }

    /** 将单条 Elasticsearch 命中转换为文章检索结果。 */
    private ArticleSearchHitResponse mapHit(Hit<ArticleDocument> hit) {
        ArticleDocument source = hit.source();
        if (source == null) {
            throw new IllegalStateException("Elasticsearch 文章命中缺少_source");
        }
        ArticleSearchHitResponse target = articleDocumentConverter.toSearchHit(source, hit.score(), hit.highlight());
        if (target.getId() == null || target.getId().isBlank()) {
            target.setId(hit.id());
        }
        return target;
    }

    /** 读取检索响应中的命中总数。 */
    private long totalHits(SearchResponse<ArticleDocument> response) {
        return response.hits().total() == null ? response.hits().hits().size() : response.hits().total().value();
    }

    /** 组装文章分页结果。 */
    private PageResult<ArticleSearchHitResponse> pageResult(List<ArticleSearchHitResponse> hits, long total,
                                                       int pageNum, int pageSize) {
        PageResult<ArticleSearchHitResponse> result = new PageResult<>();
        result.setList(hits);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    /** 获取并校验指定名称的聚合结果。 */
    private Aggregate requireAggregate(Map<String, Aggregate> aggregations, String name) {
        Aggregate aggregate = aggregations.get(name);
        if (aggregate == null) {
            throw new IllegalStateException("Elasticsearch 聚合响应缺少" + name);
        }
        return aggregate;
    }

    /** 将字符串 terms 聚合转换为词项桶列表。 */
    private List<ArticleAggregationResponse.TermBucketResponse> termBuckets(Aggregate aggregate, String name) {
        if (!aggregate.isSterms()) {
            throw new IllegalStateException("Elasticsearch 聚合" + name + "不是字符串 terms 类型");
        }
        return bucketValues(aggregate.sterms().buckets()).stream()
                .map(bucket -> new ArticleAggregationResponse.TermBucketResponse(
                        fieldValueText(bucket.key()), bucket.docCount()))
                .toList();
    }

    /** 将日期直方图聚合转换为月份桶列表。 */
    private List<ArticleAggregationResponse.MonthBucketResponse> monthBuckets(Aggregate aggregate) {
        if (!aggregate.isDateHistogram()) {
            throw new IllegalStateException("Elasticsearch 发布时间聚合不是 date_histogram 类型");
        }
        return bucketValues(aggregate.dateHistogram().buckets()).stream()
                .map(bucket -> new ArticleAggregationResponse.MonthBucketResponse(
                        monthKey(bucket), bucket.docCount()))
                .toList();
    }

    /** 将浏览量 stats 聚合转换为统计结果。 */
    private ArticleAggregationResponse.ViewCountStatsResponse viewCountStats(Aggregate aggregate) {
        if (!aggregate.isStats()) {
            throw new IllegalStateException("Elasticsearch 浏览量聚合不是 stats 类型");
        }
        StatsAggregate stats = aggregate.stats();
        return new ArticleAggregationResponse.ViewCountStatsResponse(
                stats.count(), stats.min(), stats.max(), stats.avg(), stats.sum());
    }

    /** 统一读取数组形式或键值形式的聚合桶。 */
    private <T> List<T> bucketValues(Buckets<T> buckets) {
        if (buckets.isArray()) {
            return buckets.array();
        }
        return new ArrayList<>(buckets.keyed().values());
    }

    /** 将聚合字段值转换为显示文本。 */
    private String fieldValueText(FieldValue value) {
        return value.isString() ? value.stringValue() : value._toJsonString();
    }

    /** 将日期直方图桶转换为月份文本。 */
    private String monthKey(DateHistogramBucket bucket) {
        return bucket.keyAsString() == null
                ? MONTH_FORMATTER.format(Instant.ofEpochMilli(bucket.key()))
                : bucket.keyAsString();
    }

    /** 校验并返回正整数参数。 */
    private int requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BusinessException(fieldName + "必须大于0");
        }
        return value;
    }

    /** 解析并校验检索权重。 */
    private float resolveBoost(Float value, float defaultValue, String fieldName) {
        float resolved = value == null ? defaultValue : value;
        if (!Float.isFinite(resolved) || resolved < 0) {
            throw new BusinessException(fieldName + "必须是有限的非负数");
        }
        return resolved;
    }
}
