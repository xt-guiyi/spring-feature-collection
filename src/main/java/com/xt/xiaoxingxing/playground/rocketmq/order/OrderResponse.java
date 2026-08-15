package com.xt.xiaoxingxing.playground.rocketmq.order;

import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 两套可靠消息方案共用的订单业务响应。
 *
 * <p>HTTP 调用方只需要知道最终落库的订单事实，不需要理解本次订单使用 Outbox 还是 RocketMQ
 * 事务消息。因此这里不返回事务状态、Broker 消息 ID、消息机制名称或教学说明文案。</p>
 */
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

    /**
     * 从已经提交到 PostgreSQL 的订单和明细构造响应。
     *
     * <p>{@code itemCount} 表示购买总件数，例如同一商品买 3 件应计为 3，而不是按明细行数计为 1。
     * 工厂方法故意接收落库实体，避免 Service 用请求中的数量或客户端金额拼出一个可能与数据库事实不一致的响应。</p>
     */
    public static OrderResponse from(PgOrder order, List<PgOrderProduct> items) {
        Objects.requireNonNull(order, "已落库订单不能为空");
        Objects.requireNonNull(items, "已落库订单明细不能为空");

        long totalQuantity = 0L;
        for (PgOrderProduct item : items) {
            Objects.requireNonNull(item, "订单明细不能包含null元素");
            Integer quantity = Objects.requireNonNull(item.getQuantity(), "已落库订单明细数量不能为空");
            totalQuantity = Math.addExact(totalQuantity, quantity.longValue());
        }

        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                Math.toIntExact(totalQuantity));
    }
}
