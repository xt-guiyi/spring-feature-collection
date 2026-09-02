package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PgProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
}
