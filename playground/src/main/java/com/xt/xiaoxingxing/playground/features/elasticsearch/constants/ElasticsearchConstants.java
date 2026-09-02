package com.xt.xiaoxingxing.playground.features.elasticsearch.constants;

/** Elasticsearch 文章检索投影的索引和字段约定。 */
public final class ElasticsearchConstants {

    /** Alias 是查询入口，重建索引时只切换 Alias 指向。 */
    public static final String INDEX_ALIAS = "article_search";
    public static final String INDEX_PREFIX = "article_search_v";
    public static final String INITIAL_INDEX = "article_search_v1";

    public static final String FIELD_ID = "id";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_SUMMARY = "summary";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_DIFFICULTY = "difficulty";
    public static final String FIELD_PUBLISHED_AT = "publishedAt";
    public static final String FIELD_VIEW_COUNT = "viewCount";
    public static final String FIELD_ENABLED = "enabled";
    public static final String FIELD_TITLE_SUGGEST = "titleSuggest";
    public static final String FIELD_EMBEDDING = "embedding";

    /** 禁止实例化常量类。 */
    private ElasticsearchConstants() {
    }
}
