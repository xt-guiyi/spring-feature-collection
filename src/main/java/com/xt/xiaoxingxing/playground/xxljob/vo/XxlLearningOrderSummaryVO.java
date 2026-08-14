package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 订单日报响应；保留统计版本和源数据时间窗口，便于学习者核对聚合边界。 */
@Data
public class XxlLearningOrderSummaryVO {

    private LocalDate summaryDate;
    private Integer runVersion;
    private Long orderCount;
    private Long pendingOrderCount;
    private Long paidOrderCount;
    private Long cancelledOrderCount;
    private BigDecimal totalAmount;
    /** 上海业务日对应的闭开区间起点。 */
    private LocalDateTime sourceStartAt;
    /** 上海业务日对应的闭开区间终点；该时刻本身不计入本日报。 */
    private LocalDateTime sourceEndAt;
    private Long executionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static XxlLearningOrderSummaryVO from(XxlLearningOrderSummary source) {
        if (source == null) {
            return null;
        }
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
