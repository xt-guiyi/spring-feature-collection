package com.xt.xiaoxingxing.playground.mongo.vo;

import com.xt.xiaoxingxing.playground.mongo.enums.QuestionType;
import lombok.Data;

/** 答卷中的题目快照和动态答案值。 */
@Data
public class AnswerVO {

    private String questionId;

    private String questionTitle;

    private QuestionType questionType;

    private Object value;
}
