package com.xt.xiaoxingxing.playground.features.xxljob.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 订单日报响应。 */
@Data
public class XxlOrderSummaryResponse {

    private LocalDate summaryDate;
    private Integer runVersion;
    private Long orderCount;
    private Long pendingOrderCount;
    private Long paidOrderCount;
    private Long cancelledOrderCount;
    private BigDecimal totalAmount;
    /** 统计区间起点。 */
    private LocalDateTime sourceStartAt;
    /** 统计区间终点。 */
    private LocalDateTime sourceEndAt;
    private Long executionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
