package com.xt.xiaoxingxing.playground.features.elasticsearch.support;

import com.xt.xiaoxingxing.playground.features.elasticsearch.document.ArticleDocument;

import java.time.Instant;
import java.util.List;

/** 可重复导入的固定技术文章；向量仅用于演示近邻关系，不代表真实语义模型输出。 */
public final class ArticleSeedData {

    /** 禁止实例化固定文章数据工具类。 */
    private ArticleSeedData() {
    }

    /** 返回可重复导入的八篇固定技术文章。 */
    public static List<ArticleDocument> articles() {
        return List.of(
                article(
                        "java-virtual-threads",
                        "Java 虚拟线程入门",
                        "用简单示例理解虚拟线程适合处理的阻塞型任务。",
                        "虚拟线程由 JDK 调度，能够用同步写法承载大量并发阻塞任务，但它不会让 CPU 密集计算自动变快。",
                        "Java",
                        List.of("Java", "Concurrency", "VirtualThread"),
                        "BEGINNER",
                        "2026-01-05T08:00:00Z",
                        860L,
                        List.of(0.12f, 0.10f, 0.92f, 0.85f, 0.08f, 0.05f, 0.04f, 0.03f)),
                article(
                        "spring-transactions",
                        "Spring 事务传播行为",
                        "对比 REQUIRED 与 REQUIRES_NEW 的事务边界。",
                        "Spring 事务传播行为决定方法加入现有事务还是创建新事务，学习时要同时观察异常传播和最终提交结果。",
                        "Spring",
                        List.of("Spring", "Transaction", "Java"),
                        "INTERMEDIATE",
                        "2026-01-12T08:00:00Z",
                        1120L,
                        List.of(0.10f, 0.12f, 0.90f, 0.88f, 0.10f, 0.06f, 0.04f, 0.02f)),
                article(
                        "elasticsearch-full-text-search",
                        "Elasticsearch 全文检索基础",
                        "学习倒排索引、相关性评分、过滤和高亮。",
                        "全文检索通常使用 multi_match 查询多个文本字段，再通过结构化过滤缩小候选集合，并对命中的片段进行高亮。",
                        "Elasticsearch",
                        List.of("Elasticsearch", "Search", "BM25"),
                        "BEGINNER",
                        "2026-01-20T08:00:00Z",
                        1560L,
                        List.of(0.96f, 0.90f, 0.16f, 0.10f, 0.08f, 0.05f, 0.03f, 0.02f)),
                article(
                        "elasticsearch-vector-search",
                        "Elasticsearch 向量检索实践",
                        "使用 dense_vector 和 kNN 查询查找相近文章。",
                        "dense_vector 可以保存固定维度向量，kNN 会从候选文档中查找相近向量；演示向量不等同于真实 embedding。",
                        "Elasticsearch",
                        List.of("Elasticsearch", "Vector", "KNN"),
                        "INTERMEDIATE",
                        "2026-01-27T08:00:00Z",
                        1430L,
                        List.of(0.97f, 0.88f, 0.18f, 0.12f, 0.07f, 0.05f, 0.03f, 0.02f)),
                article(
                        "redis-cache-aside",
                        "Redis Cache Aside 模式",
                        "理解读取回填、更新失效和缓存穿透边界。",
                        "Cache Aside 由应用读取缓存，未命中时查询数据源并回填；更新时通常先写数据源，再让缓存失效。",
                        "Cache",
                        List.of("Redis", "Cache", "Spring"),
                        "INTERMEDIATE",
                        "2026-02-03T08:00:00Z",
                        980L,
                        List.of(0.08f, 0.10f, 0.18f, 0.15f, 0.93f, 0.82f, 0.06f, 0.04f)),
                article(
                        "rocketmq-transactional-message",
                        "RocketMQ 事务消息流程",
                        "认识半消息、本地事务和事务状态回查。",
                        "事务消息通过半消息和事务状态确认协调发送流程，但业务幂等与数据库最终状态仍然需要应用自己负责。",
                        "Messaging",
                        List.of("RocketMQ", "Message", "Transaction"),
                        "ADVANCED",
                        "2026-02-10T08:00:00Z",
                        1260L,
                        List.of(0.06f, 0.08f, 0.14f, 0.12f, 0.86f, 0.94f, 0.09f, 0.05f)),
                article(
                        "mongodb-document-model",
                        "MongoDB 文档建模",
                        "对比嵌入文档与引用关系的适用场景。",
                        "MongoDB 建模需要根据读取聚合边界选择嵌入或引用，不能只照搬关系数据库的表拆分方式。",
                        "Database",
                        List.of("MongoDB", "Document", "Database"),
                        "BEGINNER",
                        "2026-02-17T08:00:00Z",
                        720L,
                        List.of(0.05f, 0.08f, 0.06f, 0.05f, 0.10f, 0.12f, 0.90f, 0.86f)),
                article(
                        "postgresql-index",
                        "PostgreSQL 索引与执行计划",
                        "通过 EXPLAIN 观察索引是否真正参与查询。",
                        "PostgreSQL 会根据统计信息估算成本并选择执行计划，创建索引后仍应通过 EXPLAIN 验证实际访问路径。",
                        "Database",
                        List.of("PostgreSQL", "Index", "SQL"),
                        "INTERMEDIATE",
                        "2026-02-24T08:00:00Z",
                        1340L,
                        List.of(0.04f, 0.07f, 0.05f, 0.04f, 0.08f, 0.10f, 0.92f, 0.89f))
        );
    }

    /** 构建一篇固定的 Elasticsearch 文章文档。 */
    private static ArticleDocument article(String id,
                                           String title,
                                           String summary,
                                           String content,
                                           String category,
                                           List<String> tags,
                                           String difficulty,
                                           String publishedAt,
                                           Long viewCount,
                                           List<Float> embedding) {
        return ArticleDocument.builder()
                .id(id)
                .title(title)
                .summary(summary)
                .content(content)
                .category(category)
                .tags(tags)
                .difficulty(difficulty)
                .publishedAt(Instant.parse(publishedAt))
                .viewCount(viewCount)
                .enabled(true)
                .titleSuggest(List.of(title))
                .embedding(embedding)
                .build();
    }
}
