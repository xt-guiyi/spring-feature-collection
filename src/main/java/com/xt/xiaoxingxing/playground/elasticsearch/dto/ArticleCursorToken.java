package com.xt.xiaoxingxing.playground.elasticsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCursorToken {

    @NotBlank(message = "PIT游标不能为空")
    private String pitId;

    @NotNull(message = "游标发布时间不能为空")
    private Instant publishedAt;

    @NotBlank(message = "游标文章ID不能为空")
    private String id;

    @NotNull(message = "游标分片排序值不能为空")
    private Long shardDoc;

    @NotBlank(message = "游标查询指纹不能为空")
    @Size(min = 64, max = 64, message = "游标查询指纹长度必须为64")
    private String queryFingerprint;
}
