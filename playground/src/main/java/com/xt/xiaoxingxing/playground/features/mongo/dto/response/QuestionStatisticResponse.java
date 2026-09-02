package com.xt.xiaoxingxing.playground.features.mongo.dto.response;

import com.xt.xiaoxingxing.playground.features.mongo.enums.QuestionType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 不同题型共用的统计结构，不适用的字段保持为 null 或空 Map。 */
@Data
public class QuestionStatisticResponse {

    private String questionId;

    private String questionTitle;

    private QuestionType questionType;

    private Long totalAnswered;

    private Map<String, Long> optionCounts = new LinkedHashMap<>();

    private BigDecimal average;

    private BigDecimal minimum;

    private BigDecimal maximum;
}
