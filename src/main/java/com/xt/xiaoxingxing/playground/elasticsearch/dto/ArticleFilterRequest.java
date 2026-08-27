package com.xt.xiaoxingxing.playground.elasticsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ArticleFilterRequest {

    @Size(max = 100, message = "文章分类长度不能超过100")
    private String category;

    @Size(max = 20, message = "文章标签过滤不能超过20个")
    private List<@NotBlank(message = "文章标签不能包含空白值")
            @Size(max = 100, message = "文章标签长度不能超过100") String> tags;

    @Size(max = 100, message = "文章难度长度不能超过100")
    private String difficulty;

    private Boolean enabled;

    private Instant publishedAtFrom;

    private Instant publishedAtTo;

    @PositiveOrZero(message = "最小浏览量不能小于0")
    private Long viewCountMin;

    @PositiveOrZero(message = "最大浏览量不能小于0")
    private Long viewCountMax;
}
