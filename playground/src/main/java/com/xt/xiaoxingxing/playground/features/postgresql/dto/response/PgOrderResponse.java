package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PgOrderResponse {
    private Long id;
    private Long userId;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
}
