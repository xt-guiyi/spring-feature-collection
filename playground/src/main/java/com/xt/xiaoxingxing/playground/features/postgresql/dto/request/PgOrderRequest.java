package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PgOrderRequest {
    private Long id;
    private Long userId;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
}
