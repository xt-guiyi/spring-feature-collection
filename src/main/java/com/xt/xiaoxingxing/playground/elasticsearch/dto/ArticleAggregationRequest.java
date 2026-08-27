package com.xt.xiaoxingxing.playground.elasticsearch.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleAggregationRequest extends ArticleFilterRequest {

    @Size(max = 200, message = "检索关键词长度不能超过200")
    private String keyword;
}
