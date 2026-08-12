package com.xt.xiaoxingxing.playground.rocketmq.vo;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOrderStatistics;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单事件聚合统计响应；数值来自幂等消费后的 PostgreSQL 原子 UPSERT。 */
@Data
public class RocketOrderStatisticsVO {

    private Short id;
    private Long createdCount;
    private Long paidCount;
    private Long cancelledCount;
    private BigDecimal createdAmount;
    private LocalDateTime lastEventAt;
    private LocalDateTime updatedAt;

    public static RocketOrderStatisticsVO from(MqOrderStatistics source) {
        if (source == null) {
            return null;
        }
        RocketOrderStatisticsVO target = new RocketOrderStatisticsVO();
        target.setId(source.getId());
        target.setCreatedCount(source.getCreatedCount());
        target.setPaidCount(source.getPaidCount());
        target.setCancelledCount(source.getCancelledCount());
        target.setCreatedAmount(source.getCreatedAmount());
        target.setLastEventAt(source.getLastEventAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
