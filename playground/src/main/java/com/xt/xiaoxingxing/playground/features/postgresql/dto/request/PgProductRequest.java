package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PgProductRequest {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
}
