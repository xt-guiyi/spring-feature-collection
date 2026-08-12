package com.xt.xiaoxingxing.playground.rocketmq.message;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单领域事件负载。
 *
 * <p>{@code productIds} 可用于失效商品缓存；库存恢复时仍必须重新查询持久化订单明细，不能把消息中的历史快照
 * 当作库存事实。</p>
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
