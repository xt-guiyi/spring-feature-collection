package com.xt.xiaoxingxing.playground.features.elasticsearch.document;

import com.xt.xiaoxingxing.playground.features.elasticsearch.constants.ElasticsearchConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.Instant;
import java.util.List;

/** 文章在 Elasticsearch 中的检索投影；字段 mapping 由索引管理器显式创建。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = ElasticsearchConstants.INDEX_ALIAS, createIndex = false,
        writeTypeHint = WriteTypeHint.FALSE, storeIdInSource = true)
public class ArticleDocument {

    @Id
    private String id;
    private String title;
    private String summary;
    private String content;
    private String category;
    private List<String> tags;
    private String difficulty;

    /** 使用 Spring Data 内置转换器读写 Java Client 的 ISO 日期或毫秒时间戳。 */
    @Field(type = FieldType.Date, format = {
            DateFormat.strict_date_optional_time,
            DateFormat.strict_date_optional_time_nanos,
            DateFormat.epoch_millis
    }, pattern = {
            "uuuu-MM-dd'T'HH:mm:ssXXX",
            "uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX"
    })
    private Instant publishedAt;
    private Long viewCount;
    private Boolean enabled;
    private List<String> titleSuggest;
    private List<Float> embedding;
}
