package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;

@Data
public class PgProductProfileResponse {
    private Long id;
    private Long productId;
    private JsonNode attributes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
