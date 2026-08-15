package com.xt.xiaoxingxing.playground.rocketmq.order.outbox;

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
 * Transactional Outbox 订单的业务入口。
 *
 * <p>Controller 只表达“创建订单”和“支付订单”，不会让调用方选择 Topic、Tag、发送模式或重试次数。
 * 两个接口都返回统一的订单事实，不暴露 Outbox eventId、Broker messageId 或发布状态。</p>
 */
@Validated
@RestController("outboxOrderController")
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/outbox/orders")
public class OrderController {

    private final OrderService orderService;

    /** 创建 PENDING 订单、写入明细、条件扣减库存，并在同一事务登记创建及超时消息意图。 */
    @PostMapping
    public Result<OrderResponse> create(@Valid @RequestBody CompleteOrderCreateRequest request) {
        return Result.ok(orderService.createOrder(request));
    }

    /** 只有 PENDING 订单能够转为 PAID；重复支付不会重复生成 ORDER_PAID 事件。 */
    @PostMapping("/{orderId}/pay")
    public Result<OrderResponse> pay(
            @PathVariable @Positive(message = "orderId必须大于0") Long orderId) {
        return Result.ok(orderService.payOrder(orderId));
    }
}
