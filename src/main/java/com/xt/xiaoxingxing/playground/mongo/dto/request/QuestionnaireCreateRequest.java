package com.xt.xiaoxingxing.playground.mongo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建问卷时只创建草稿，题目通过独立数组更新接口逐步添加。 */
@Data
public class QuestionnaireCreateRequest {

    @NotBlank(message = "问卷标题不能为空")
    @Size(max = 200, message = "问卷标题不能超过200个字符")
    private String title;

    @Size(max = 2000, message = "问卷说明不能超过2000个字符")
    private String description;

    @NotNull(message = "创建人不能为空")
    @Positive(message = "创建人ID必须大于0")
    private Long createdByUserId;
}
