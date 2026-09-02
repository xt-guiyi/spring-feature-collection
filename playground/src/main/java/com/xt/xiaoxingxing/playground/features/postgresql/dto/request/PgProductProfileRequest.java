package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class PgProductProfileRequest {
    private Long id;
    private Long productId;
    private JsonNode attributes;
}
