package com.xt.xiaoxingxing.playground.features.mongo.enums;

/** 问卷生命周期：草稿可以编辑，发布后可以答题，关闭后只允许查询和统计。 */
public enum QuestionnaireStatus {
    DRAFT,
    PUBLISHED,
    CLOSED
}
