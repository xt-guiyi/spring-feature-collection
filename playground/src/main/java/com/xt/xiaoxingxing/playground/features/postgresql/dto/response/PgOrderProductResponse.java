package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PgOrderProductResponse {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
}
