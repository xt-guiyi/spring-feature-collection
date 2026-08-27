package com.xt.xiaoxingxing.playground.elasticsearch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleHybridRequest extends ArticleFilterRequest {

    @NotBlank(message = "混合检索关键词不能为空")
    @Size(max = 200, message = "检索关键词长度不能超过200")
    private String keyword;

    @NotEmpty(message = "查询向量不能为空")
    private List<@NotNull(message = "查询向量不能包含空值") Float> queryVector;

    @Positive(message = "KNN 的k必须大于0")
    private Integer k;

    @Positive(message = "KNN 的numCandidates必须大于0")
    private Integer numCandidates;

    @DecimalMin(value = "0.0", message = "文本权重不能小于0")
    private Float textBoost;

    @DecimalMin(value = "0.0", message = "向量权重不能小于0")
    private Float vectorBoost;
}
