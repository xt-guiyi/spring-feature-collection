package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 使用 PostgreSQL JSONB {@code ||} 合并到 attributes 顶层的对象补丁。 */
@Data
public class ProductProfileAttributesMergeRequest {

    private JsonNode attributes;
}
