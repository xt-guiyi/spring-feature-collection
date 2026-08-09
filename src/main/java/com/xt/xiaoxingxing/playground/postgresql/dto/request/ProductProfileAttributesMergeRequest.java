package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** 使用 PostgreSQL JSONB {@code ||} 合并到 attributes 顶层的对象补丁。 */
@Data
public class ProductProfileAttributesMergeRequest {

    private JsonNode attributes;
}
