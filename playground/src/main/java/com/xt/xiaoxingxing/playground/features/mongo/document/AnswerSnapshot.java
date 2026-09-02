package com.xt.xiaoxingxing.playground.features.mongo.document;

import com.xt.xiaoxingxing.playground.features.mongo.enums.QuestionType;
import lombok.Data;

/**
 * 答卷内嵌答案。
 *
 * <p>同时保存题目标题和题型快照，使历史答卷不必再次读取问卷才能展示当时题意。
 * value 会在 Service 中按题型规范化，避免把 Controller 收到的 JsonNode 直接持久化。</p>
 */
@Data
public class AnswerSnapshot {

    private String questionId;

    private String questionTitle;

    private QuestionType questionType;

    private Object value;
}
