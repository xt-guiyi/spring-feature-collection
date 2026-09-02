package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleSearchRequest extends ArticleFilterRequest {

    @Size(max = 200, message = "检索关键词长度不能超过200")
    private String keyword;

    @NotNull(message = "页码不能为空")
    @Positive(message = "页码必须大于0")
    private Integer pageNum = 1;

    @NotNull(message = "每页数量不能为空")
    @Positive(message = "每页数量必须大于0")
    private Integer pageSize = 10;

    @NotBlank(message = "排序字段不能为空")
    private String sortBy = "relevance";

    @NotBlank(message = "排序方向不能为空")
    private String sortOrder = "desc";
}
