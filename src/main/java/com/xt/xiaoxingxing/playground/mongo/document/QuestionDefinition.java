package com.xt.xiaoxingxing.playground.mongo.document;

import com.xt.xiaoxingxing.playground.mongo.enums.QuestionType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内嵌在问卷文档中的题目定义，不单独创建 collection。
 *
 * <p>标题、类型、必填标记属于所有题目的稳定结构；不同题型特有的 options、maxLength、
 * min/max 等规则放在 settings 中，正好展示 MongoDB“稳定外壳 + 动态内容”的建模方式。</p>
 */
@Data
public class QuestionDefinition {

    /** 内嵌文档没有 MongoDB _id，使用 UUID 字符串作为稳定业务ID。 */
    private String id;

    private String title;

    private QuestionType type;

    private boolean required;

    private Map<String, Object> settings = new LinkedHashMap<>();
}
