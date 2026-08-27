package com.xt.xiaoxingxing.playground.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.config.ElasticsearchConstants;
import com.xt.xiaoxingxing.playground.elasticsearch.config.ElasticsearchProperties;
import com.xt.xiaoxingxing.playground.elasticsearch.document.ArticleDocument;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleRequestValidator;
import com.xt.xiaoxingxing.playground.elasticsearch.support.ArticleSeedData;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleBulkResultVO;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleIndexStatusVO;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleRebuildVO;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 使用 Elasticsearch Java API Client 管理文章索引生命周期。
 * client.indices().create(...)         // 创建索引
 * client.indices().delete(...)         // 删除索引
 * client.indices().exists(...)         // 判断索引是否存在
 * client.indices().get(...)            // 查询索引
 * client.indices().getMapping(...)     // 查询 Mapping
 * client.indices().putMapping(...)     // 修改 Mapping
 * client.indices().getAlias(...)       // 查询 Alias
 * client.indices().updateAliases(...)  // 添加、删除或切换 Alias
 * client.indices().refresh(...)        // 刷新索引
 * */
@Service
public class ArticleIndexManager {

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ArticleRequestValidator articleRequestValidator;

    /** 创建文章索引生命周期管理器。 */
    public ArticleIndexManager(ElasticsearchClient client,
                               ElasticsearchProperties properties,
                               ArticleRequestValidator articleRequestValidator) {
        this.client = client;
        this.properties = properties;
        this.articleRequestValidator = articleRequestValidator;
    }

    /**
     * 首次创建时把 Mapping、Settings 和写 Alias 放进同一个请求，避免出现只有索引没有稳定入口的中间状态。
     */
    public ArticleIndexStatusVO initialize() {
        try {
            if (aliasExists()) {
                return status();
            }
            if (indexExists(ElasticsearchConstants.INDEX_ALIAS)) {
                throw new BusinessException("已存在与 Alias 同名的实体索引：" + ElasticsearchConstants.INDEX_ALIAS);
            }

            if (indexExists(ElasticsearchConstants.INITIAL_INDEX)) {
                addWriteAlias(ElasticsearchConstants.INITIAL_INDEX);
            } else {
                createIndex(ElasticsearchConstants.INITIAL_INDEX, true);
            }
            return status();
        } catch (IOException exception) {
            throw new IllegalStateException("初始化 Elasticsearch 文章索引失败", exception);
        }
    }

    /** 固定 ID 使重复导入变成覆盖写入，便于反复练习同一组查询。 */
    public ArticleBulkResultVO seed() {
        try {
            requireWriteIndex(aliasState());
            List<ArticleDocument> articles = ArticleSeedData.articles();
            articleRequestValidator.validateBulkSize(articles.size());
            articles.forEach(article -> articleRequestValidator.validateEmbedding(article.getEmbedding()));

            var request = new co.elastic.clients.elasticsearch.core.BulkRequest.Builder()
                    .index(ElasticsearchConstants.INDEX_ALIAS)
                    .requireAlias(true)
                    .refresh(Refresh.WaitFor);
            for (ArticleDocument article : articles) {
                request.operations(operation -> operation.index(index -> index
                        .id(article.getId())
                        .document(article)));
            }

            BulkResponse response = client.bulk(request.build());
            List<String> failures = new ArrayList<>();
            response.items().forEach(item -> {
                if (item.error() != null) {
                    failures.add(item.id() + ": " + item.error().reason());
                }
            });
            if (!failures.isEmpty()) {
                // Bulk HTTP 请求成功不代表每一个 item 都成功，必须逐项检查后再报告结果。
                throw new BusinessException("固定文章 Bulk 写入存在失败项：" + String.join("；", failures));
            }

            ArticleBulkResultVO result = new ArticleBulkResultVO();
            result.setTotal(articles.size());
            result.setSucceeded(response.items().size());
            result.setFailed(0);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Bulk 写入 Elasticsearch 固定文章失败", exception);
        }
    }

    /** 查询文章索引、写 Alias、文档数量和集群健康状态。 */
    public ArticleIndexStatusVO status() {
        try {
            AliasState aliasState = aliasState();
            long documentCount = aliasState.indices().isEmpty()
                    ? 0L
                    : count(ElasticsearchConstants.INDEX_ALIAS);
            var health = client.cluster().health();

            ArticleIndexStatusVO result = new ArticleIndexStatusVO();
            result.setAlias(ElasticsearchConstants.INDEX_ALIAS);
            result.setIndices(aliasState.indices());
            result.setWriteIndex(aliasState.writeIndex());
            result.setDocumentCount(documentCount);
            result.setClusterHealth(health.status().jsonValue());
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("查询 Elasticsearch 文章索引状态失败", exception);
        }
    }

    /** 重建文章物理索引，并在校验通过后切换 Alias。 */
    public ArticleRebuildVO rebuild() {
        try {
            AliasState aliasState = aliasState();
            String sourceIndex = requireWriteIndex(aliasState);
            if (aliasState.indices().size() != 1) {
                throw new BusinessException("文章 Alias 必须只指向一个物理索引后才能重建");
            }

            String targetIndex = nextIndexName();
            createIndex(targetIndex, false);

            ReindexResponse reindex = client.reindex(request -> request
                    .source(source -> source.index(sourceIndex))
                    .dest(destination -> destination.index(targetIndex))
                    .refresh(true)
                    .waitForCompletion(true));
            if (Boolean.TRUE.equals(reindex.timedOut())) {
                throw new BusinessException("Reindex 执行超时，Alias 未切换");
            }
            if (!reindex.failures().isEmpty()) {
                List<String> failures = reindex.failures().stream()
                        .map(failure -> failure.id() + ": " + failure.cause().reason())
                        .toList();
                throw new BusinessException("Reindex 存在失败项，Alias 未切换：" + String.join("；", failures));
            }

            long sourceCount = count(sourceIndex);
            long targetCount = count(targetIndex);
            if (sourceCount != targetCount) {
                // 数量不一致时保留新旧索引供排查，但绝不把稳定 Alias 指向不完整的新索引。
                throw new BusinessException("Reindex 数量不一致，Alias 未切换：source="
                        + sourceCount + "，target=" + targetCount);
            }

            switchAlias(aliasState.indices(), targetIndex);

            ArticleRebuildVO result = new ArticleRebuildVO();
            result.setAlias(ElasticsearchConstants.INDEX_ALIAS);
            result.setSourceIndex(sourceIndex);
            result.setTargetIndex(targetIndex);
            result.setDocumentCount(targetCount);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("重建 Elasticsearch 文章索引失败", exception);
        }
    }

    /** 删除指定索引或 Alias 下的全部文章数据。 */
    public long deleteAllDocuments(String indexName) {
        try {
            DeleteByQueryResponse response = client.deleteByQuery(request -> request
                    .index(indexName)
                    .query(query -> query.matchAll(matchAll -> matchAll))
                    .refresh(true)
                    .waitForCompletion(true));
            if (Boolean.TRUE.equals(response.timedOut())) {
                throw new BusinessException("删除 Elasticsearch 索引全部数据超时：" + indexName);
            }
            if (response.versionConflicts() != null && response.versionConflicts() > 0) {
                throw new BusinessException("删除 Elasticsearch 索引全部数据存在版本冲突："
                        + response.versionConflicts());
            }
            if (!response.failures().isEmpty()) {
                List<String> failures = response.failures().stream()
                        .map(failure -> failure.id() + ": " + failure.cause().reason())
                        .toList();
                throw new BusinessException("删除 Elasticsearch 索引全部数据存在失败项："
                        + String.join("；", failures));
            }
            return response.deleted() == null ? 0L : response.deleted();
        } catch (IOException exception) {
            throw new IllegalStateException("删除 Elasticsearch 索引全部数据失败：" + indexName, exception);
        }
    }

    /** 删除指定索引或 Alias 下的一篇文章。 */
    public boolean deleteDocument(String indexName, String id) {
        try {
            Result result = client.delete(request -> request
                            .index(indexName)
                            .id(id)
                            .refresh(Refresh.WaitFor))
                            .result();
            return result != Result.NotFound;
        } catch (ElasticsearchException exception) {
            if (exception.status() == 404) {
                return false;
            }
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("删除 Elasticsearch 文档失败：index="
                    + indexName + "，id=" + id, exception);
        }
    }

    /** 删除指定的 Elasticsearch 索引。 */
    public boolean deleteIndex(String indexName) {
        try {
            return client.indices().delete(request -> request.index(indexName)).acknowledged();
        } catch (IOException exception) {
            throw new IllegalStateException("删除 Elasticsearch 索引失败：" + indexName, exception);
        }
    }

    /** 按文章 Mapping 和配置创建指定物理索引。 */
    private void createIndex(String indexName, boolean attachAlias) throws IOException {
        // 手动构建索引请求对象 + 发送请求给 Elasticsearch 。区别于addWriteAlias 、indexExists方法Lambda 写法，自动构建请求对象 + 发送（算是简写）
        CreateIndexRequest.Builder request = new CreateIndexRequest.Builder()
                .index(indexName)
                .settings(settings -> settings
                        .numberOfShards(String.valueOf(properties.getNumberOfShards()))
                        .numberOfReplicas(String.valueOf(properties.getNumberOfReplicas())))
                .mappings(articleMapping());
        if (attachAlias) {
            request.aliases(ElasticsearchConstants.INDEX_ALIAS, alias -> alias.isWriteIndex(true));
        }

        var response = client.indices().create(request.build());
        if (!response.acknowledged()) {
            throw new IllegalStateException("Elasticsearch 未确认索引创建请求：" + indexName);
        }
        if (!response.shardsAcknowledged()) {
            throw new IllegalStateException("Elasticsearch 已创建索引但主分片尚未就绪：" + indexName);
        }

// lambda 写法
//        var response = client.indices().create(request -> {
//            request.index(indexName)
//                    .settings(settings -> settings
//                            .numberOfShards(String.valueOf(properties.getNumberOfShards()))
//                            .numberOfReplicas(String.valueOf(properties.getNumberOfReplicas())))
//                    .mappings(articleMapping());
//
//            if (attachAlias) {
//                request.aliases(
//                        ElasticsearchConstants.INDEX_ALIAS,
//                        alias -> alias.isWriteIndex(true)
//                );
//            }
//
//            return request;
//        });
    }

    /** 构建文章索引的字段 Mapping。 */
    private TypeMapping articleMapping() {
        return TypeMapping.of(mapping -> mapping
                .dynamic(DynamicMapping.Strict)
                .properties(ElasticsearchConstants.FIELD_ID, property -> property.keyword(keyword -> keyword))
                .properties(ElasticsearchConstants.FIELD_TITLE, property -> property.text(text -> text
                        .analyzer("standard")
                        .fields("keyword", keywordField -> keywordField.keyword(keyword -> keyword))))
                .properties(ElasticsearchConstants.FIELD_SUMMARY, property -> property.text(text -> text
                        .analyzer("standard")))
                .properties(ElasticsearchConstants.FIELD_CONTENT, property -> property.text(text -> text
                        .analyzer("standard")))
                .properties(ElasticsearchConstants.FIELD_CATEGORY, property -> property.keyword(keyword -> keyword))
                .properties(ElasticsearchConstants.FIELD_TAGS, property -> property.keyword(keyword -> keyword))
                .properties(ElasticsearchConstants.FIELD_DIFFICULTY, property -> property.keyword(keyword -> keyword))
                .properties(ElasticsearchConstants.FIELD_PUBLISHED_AT, property -> property.date(date -> date
                        .format("strict_date_optional_time||epoch_millis")))
                .properties(ElasticsearchConstants.FIELD_VIEW_COUNT, property -> property.long_(number -> number))
                .properties(ElasticsearchConstants.FIELD_ENABLED, property -> property.boolean_(bool -> bool))
                .properties(ElasticsearchConstants.FIELD_TITLE_SUGGEST, property -> property.completion(completion -> completion
                        .analyzer("standard")))
                .properties(ElasticsearchConstants.FIELD_EMBEDDING, property -> property.denseVector(vector -> vector
                        .dims(properties.getVectorDimensions())
                        .index(true)
                        .similarity(DenseVectorSimilarity.Cosine))));
    }

    /** 为指定物理索引绑定文章写 Alias。 */
    private void addWriteAlias(String indexName) throws IOException {
        var response = client.indices().updateAliases(request -> request.actions(action -> action.add(add -> add
                .index(indexName)
                .alias(ElasticsearchConstants.INDEX_ALIAS)
                .isWriteIndex(true))));
        if (!response.acknowledged()) {
            throw new IllegalStateException("Elasticsearch 未确认 Alias 创建请求");
        }
    }

    /** Update Aliases API 中的多个 action 由集群状态一次性原子应用。 */
    private void switchAlias(List<String> sourceIndices, String targetIndex) throws IOException {
        UpdateAliasesRequest.Builder request = new UpdateAliasesRequest.Builder();
        sourceIndices.forEach(index -> request.actions(action -> action.remove(remove -> remove
                .index(index)
                .alias(ElasticsearchConstants.INDEX_ALIAS)
                .mustExist(true))));
        request.actions(action -> action.add(add -> add
                .index(targetIndex)
                .alias(ElasticsearchConstants.INDEX_ALIAS)
                .isWriteIndex(true)));

        var response = client.indices().updateAliases(request.build());
        if (!response.acknowledged()) {
            throw new IllegalStateException("Elasticsearch 未确认 Alias 原子切换请求");
        }
    }

    /** 读取文章 Alias 当前关联的物理索引和写索引。 */
    private AliasState aliasState() throws IOException {
        if (!aliasExists()) {
            return new AliasState(List.of(), null);
        }

        GetAliasResponse response = client.indices().getAlias(request -> request
                .name(ElasticsearchConstants.INDEX_ALIAS));
        List<String> indices = response.aliases().keySet().stream()
                .sorted()
                .toList();
        List<String> explicitWriteIndices = indices.stream()
                .filter(index -> Boolean.TRUE.equals(response.aliases()
                        .get(index)
                        .aliases()
                        .get(ElasticsearchConstants.INDEX_ALIAS)
                        .isWriteIndex()))
                .toList();
        if (explicitWriteIndices.size() > 1) {
            throw new BusinessException("文章 Alias 存在多个写索引");
        }

        String writeIndex = explicitWriteIndices.isEmpty() ? null : explicitWriteIndices.getFirst();
        if (writeIndex == null && indices.size() == 1) {
            Boolean writeFlag = response.aliases()
                    .get(indices.getFirst())
                    .aliases()
                    .get(ElasticsearchConstants.INDEX_ALIAS)
                    .isWriteIndex();
            // 单索引 Alias 只有在未显式声明 false 时，Elasticsearch 才会把它当作隐式写索引。
            if (writeFlag == null) {
                writeIndex = indices.getFirst();
            }
        }
        return new AliasState(indices, writeIndex);
    }

    /** 校验并返回文章 Alias 的唯一写索引。 */
    private String requireWriteIndex(AliasState aliasState) {
        if (aliasState.indices().isEmpty()) {
            throw new BusinessException("文章索引尚未初始化，请先调用 /index/initialize");
        }
        if (aliasState.writeIndex() == null) {
            throw new BusinessException("文章 Alias 未配置唯一写索引");
        }
        return aliasState.writeIndex();
    }

    /** 计算下一个可用的版本化物理索引名称。 */
    private String nextIndexName() throws IOException {
        List<String> versionedIndices = client.indices().get(request -> request
                        .index(ElasticsearchConstants.INDEX_PREFIX + "*")
                        .allowNoIndices(true)
                        .expandWildcards(ExpandWildcard.All)
                        .ignoreUnavailable(true))
                .indices()
                .keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        int maxVersion = versionedIndices.stream()
                .map(name -> name.substring(ElasticsearchConstants.INDEX_PREFIX.length()))
                .filter(suffix -> suffix.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return ElasticsearchConstants.INDEX_PREFIX + (maxVersion + 1);
    }

    /** 统计指定索引的完整文档数量。 */
    private long count(String indexName) throws IOException {
        var response = client.count(request -> request.index(indexName));
        if (response.shards().failed().intValue() > 0) {
            // Count 允许返回部分分片结果；迁移校验必须拒绝用不完整数字切换 Alias。
            throw new IllegalStateException("Elasticsearch 统计文档数存在失败分片：index="
                    + indexName + "，failed=" + response.shards().failed());
        }
        return response.count();
    }

    /** 判断文章 Alias 是否存在。 */
    private boolean aliasExists() throws IOException {
        return client.indices().existsAlias(request -> request
                        .name(ElasticsearchConstants.INDEX_ALIAS))
                .value();
    }

    /** 判断指定物理索引是否存在。 */
    private boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(request -> request.index(indexName)).value();
    }

    private record AliasState(List<String> indices, String writeIndex) {
    }
}
