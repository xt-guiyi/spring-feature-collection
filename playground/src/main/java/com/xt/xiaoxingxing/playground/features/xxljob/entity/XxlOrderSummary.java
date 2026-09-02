package com.xt.xiaoxingxing.playground.features.xxljob.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 订单日报。 */
@Data
public class XxlOrderSummary {
    private LocalDate summaryDate;
    private Integer runVersion;
    private Long orderCount;
    private Long pendingOrderCount;
    private Long paidOrderCount;
    private Long cancelledOrderCount;
    private BigDecimal totalAmount;
    private LocalDateTime sourceStartAt;
    private LocalDateTime sourceEndAt;
    private Long executionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
