package com.xt.xiaoxingxing.playground.xxljob.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 上海业务日内订单事实的版本化汇总。 */
@Data
public class XxlLearningOrderSummary {
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
