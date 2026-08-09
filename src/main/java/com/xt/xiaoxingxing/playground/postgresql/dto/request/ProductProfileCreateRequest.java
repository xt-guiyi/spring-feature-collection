package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** 创建商品扩展信息；attributes 必须是 JSON 对象，由 Service 统一校验。 */
@Data
public class ProductProfileCreateRequest {

    private Long productId;

    private JsonNode attributes;
}
