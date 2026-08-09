package com.xt.xiaoxingxing.playground.mongo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 草稿基本信息更新；expectedVersion 统一放在查询参数中。 */
@Data
public class QuestionnaireUpdateRequest {

    @NotBlank(message = "问卷标题不能为空")
    @Size(max = 200, message = "问卷标题不能超过200个字符")
    private String title;

    @Size(max = 2000, message = "问卷说明不能超过2000个字符")
    private String description;
}
