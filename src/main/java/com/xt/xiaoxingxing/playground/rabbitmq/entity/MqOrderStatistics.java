package com.xt.xiaoxingxing.playground.rabbitmq.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 消费 ORDER_* 事件后得到的一行全局订单统计。 */
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
