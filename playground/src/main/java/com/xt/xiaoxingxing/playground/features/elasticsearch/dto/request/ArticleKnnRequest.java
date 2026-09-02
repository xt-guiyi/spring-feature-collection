package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleKnnRequest extends ArticleFilterRequest {

    @NotEmpty(message = "查询向量不能为空")
    private List<@NotNull(message = "查询向量不能包含空值") Float> queryVector;

    @Positive(message = "KNN 的k必须大于0")
    private Integer k;

    @Positive(message = "KNN 的numCandidates必须大于0")
    private Integer numCandidates;
}
