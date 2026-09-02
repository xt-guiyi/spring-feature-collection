package com.xt.xiaoxingxing.playground.features.mongo.dto.request;

import com.xt.xiaoxingxing.playground.features.mongo.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态题目请求。
 *
 * <p>settings 示例：选择题使用 options，文本题使用 maxLength，数字和评分题使用 min/max。</p>
 */
@Data
public class QuestionCreateRequest {

    @NotBlank(message = "题目标题不能为空")
    @Size(max = 500, message = "题目标题不能超过500个字符")
    private String title;

    @NotNull(message = "题目类型不能为空")
    private QuestionType type;

    private Boolean required = false;

    @NotNull(message = "题目设置不能为空")
    private Map<String, Object> settings = new LinkedHashMap<>();
}
