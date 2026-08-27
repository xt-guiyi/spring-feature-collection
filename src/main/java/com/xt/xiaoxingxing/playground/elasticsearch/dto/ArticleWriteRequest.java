package com.xt.xiaoxingxing.playground.elasticsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** 写入 Elasticsearch 文章检索投影的请求。 */
@Data
public class ArticleWriteRequest {

    @NotBlank(message = "文章标题不能为空")
    private String title;

    @NotBlank(message = "文章摘要不能为空")
    private String summary;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    @NotBlank(message = "文章分类不能为空")
    private String category;

    @NotEmpty(message = "文章标签不能为空")
    private List<@NotBlank(message = "文章标签不能包含空白值") String> tags;

    @NotBlank(message = "文章难度不能为空")
    private String difficulty;

    @NotNull(message = "发布时间不能为空")
    private Instant publishedAt;

    @NotNull(message = "浏览量不能为空")
    @PositiveOrZero(message = "浏览量不能小于0")
    private Long viewCount;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    @NotEmpty(message = "文章向量不能为空")
    private List<@NotNull(message = "文章向量不能包含空值") Float> embedding;
}
