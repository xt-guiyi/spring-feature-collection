package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 订单日报响应。 */
@Data
public class XxlLearningOrderSummaryVO {

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

    public static XxlLearningOrderSummaryVO from(XxlLearningOrderSummary source) {
        XxlLearningOrderSummaryVO target = new XxlLearningOrderSummaryVO();
        target.setSummaryDate(source.getSummaryDate());
        target.setRunVersion(source.getRunVersion());
        target.setOrderCount(source.getOrderCount());
        target.setPendingOrderCount(source.getPendingOrderCount());
        target.setPaidOrderCount(source.getPaidOrderCount());
        target.setCancelledOrderCount(source.getCancelledOrderCount());
        target.setTotalAmount(source.getTotalAmount());
        target.setSourceStartAt(source.getSourceStartAt());
        target.setSourceEndAt(source.getSourceEndAt());
        target.setExecutionId(source.getExecutionId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
