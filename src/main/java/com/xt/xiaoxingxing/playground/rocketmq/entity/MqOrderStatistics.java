package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消费 ORDER_* 事件后维护的一行全局订单统计。
 *
 * <p>固定主键 1 配合 UPSERT 让增量在数据库内原子完成；调用方必须先成功抢到消费幂等记录，
 * 否则重复投递会重复累计。</p>
 */
@Data
public class MqOrderStatistics {

    private Short id;
    private Long createdCount;
    private Long paidCount;
    private Long cancelledCount;
    private BigDecimal createdAmount;
    private LocalDateTime lastEventAt;
    private LocalDateTime updatedAt;
}
