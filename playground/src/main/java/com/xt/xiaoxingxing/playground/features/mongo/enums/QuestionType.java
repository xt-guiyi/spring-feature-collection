package com.xt.xiaoxingxing.playground.features.mongo.enums;

/**
 * 动态题目类型。
 *
 * <p>同一个 answers.value 字段会根据题型保存不同 BSON 值：文本、数组、Decimal128 或整数。</p>
 */
public enum QuestionType {
    TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    NUMBER,
    RATING
}
