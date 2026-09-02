package com.xt.xiaoxingxing.playground.features.mongo.dto.response;

import com.xt.xiaoxingxing.playground.features.mongo.enums.QuestionType;
import lombok.Data;

import java.util.Map;

/** 前端问卷中的动态题目结构。 */
@Data
public class QuestionResponse {

    private String id;

    private String title;

    private QuestionType type;

    private boolean required;

    private Map<String, Object> settings;
}
