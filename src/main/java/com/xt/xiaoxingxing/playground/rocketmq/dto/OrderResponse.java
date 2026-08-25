package com.xt.xiaoxingxing.playground.rocketmq.dto;

import com.xt.xiaoxingxing.playground.rocketmq.entity.Order;
import com.xt.xiaoxingxing.playground.rocketmq.entity.OrderItem;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** 订单响应。 */
@Getter
public final class OrderResponse {

    private final Long orderId;
    private final String orderNo;
    private final String status;
    private final BigDecimal totalAmount;
    private final Integer itemCount;

    private OrderResponse(Long orderId,
                          String orderNo,
                          String status,
                          BigDecimal totalAmount,
                          Integer itemCount) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
    }

    /** 根据订单和订单明细生成订单响应。 */
    public static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                Math.toIntExact(items.stream().mapToLong(OrderItem::getQuantity).sum()));
    }
}
