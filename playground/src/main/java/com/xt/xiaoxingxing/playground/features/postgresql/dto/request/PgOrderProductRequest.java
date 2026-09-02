package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PgOrderProductRequest {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
}
