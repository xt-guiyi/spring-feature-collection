package com.xt.xiaoxingxing.playground.rocketmq.order.transaction;

/**
 * 订单状态条件更新没有命中时抛出的业务并发异常。
 *
 * <p>它不表示 SQL 执行失败，而是说明另一个请求已经先把订单从 PENDING 推进到 PAID 或 CANCELLED。
 * 支付接口需要把它作为失败返回；重复超时取消则会重读订单，非 PENDING 时按幂等成功处理。</p>
 */
public class OrderStateConflictException extends RuntimeException {

    public OrderStateConflictException(String message) {
        super(message);
    }
}
