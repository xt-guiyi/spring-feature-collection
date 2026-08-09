package com.xt.xiaoxingxing.playground.rabbitmq.message;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单事件负载。
 *
 * <p>productIds 用来删除受影响商品缓存；库存恢复仍然重新查询 order_products，不能依赖消息里的旧快照。</p>
 */
@Data
public class OrderEventPayload {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private List<Long> productIds;
}
