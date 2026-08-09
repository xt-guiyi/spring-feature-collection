package com.xt.xiaoxingxing.playground.mongo.dto.request;

import com.xt.xiaoxingxing.playground.mongo.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 使用 PUT 完整替换一个草稿题目，但保留原有 questionId。 */
@Data
public class QuestionUpdateRequest {

    @NotBlank(message = "题目标题不能为空")
    @Size(max = 500, message = "题目标题不能超过500个字符")
    private String title;

    @NotNull(message = "题目类型不能为空")
    private QuestionType type;

    private Boolean required = false;

    @NotNull(message = "题目设置不能为空")
    private Map<String, Object> settings = new LinkedHashMap<>();
}
