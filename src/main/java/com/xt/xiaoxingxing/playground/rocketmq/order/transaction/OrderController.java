package com.xt.xiaoxingxing.playground.rocketmq.order.transaction;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.rocketmq.order.OrderResponse;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RocketMQ 事务消息订单的业务入口。
 *
 * <p>HTTP 只返回订单事实，不暴露 transactionId、Broker messageId 或半消息状态。数据库已经提交、
 * 但半消息 commit RPC 结果不明确时，Broker 会根据持久化事务记录回查，业务调用方仍得到真实订单结果。</p>
 */
@Validated
@RestController("transactionOrderController")
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/transaction/orders")
public class OrderController {

    private final OrderService orderService;

    /** 创建 PENDING 订单、写入明细并条件扣减库存。 */
    @PostMapping
    public Result<OrderResponse> create(@Valid @RequestBody CompleteOrderCreateRequest request) {
        return Result.ok(orderService.createOrder(request));
    }

    /** 仅事务消息 CREATE 链创建的 PENDING 订单可通过此入口支付。 */
    @PostMapping("/{orderId}/pay")
    public Result<OrderResponse> pay(
            @PathVariable @Positive(message = "orderId必须大于0") Long orderId) {
        return Result.ok(orderService.payOrder(orderId));
    }
}
