package com.xt.xiaoxingxing.playground.rocketmq.controller;

import com.xt.xiaoxingxing.playground.rocketmq.dto.CreateOrderRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.OrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.service.OrderService;
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

/** 订单控制器。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/orders")
public class OrderController {

    private final OrderService orderService;

    /** 创建订单。 */
    @PostMapping
    public Result<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.createOrder(request));
    }

    /** 支付订单。 */
    @PostMapping("/{orderId}/pay")
    public Result<OrderResponse> pay(
            @PathVariable @Positive(message = "orderId必须大于0") Long orderId) {
        return Result.ok(orderService.payOrder(orderId));
    }
}
