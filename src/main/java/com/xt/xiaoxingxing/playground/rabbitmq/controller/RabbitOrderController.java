package com.xt.xiaoxingxing.playground.rabbitmq.controller;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.service.RabbitOrderApplicationService;
import com.xt.xiaoxingxing.playground.rabbitmq.vo.RabbitOrderCreateVO;
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

/** Transactional Outbox 可靠订单入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rabbitmq/orders")
public class RabbitOrderController {

    private final RabbitOrderApplicationService orderApplicationService;

    /** 创建订单、扣库存并在同一个数据库事务写入创建事件和超时事件。 */
    @PostMapping
    public Result<RabbitOrderCreateVO> create(@Valid @RequestBody CompleteOrderCreateRequest request) {
        return Result.ok(orderApplicationService.createOrder(request));
    }

    /** 条件更新 PENDING -> PAID，成功后在同一个事务写入 ORDER_PAID Outbox。 */
    @PostMapping("/{orderId}/pay")
    public Result<Boolean> pay(@PathVariable @Positive(message = "orderId必须大于0") Long orderId) {
        return Result.ok(orderApplicationService.payOrder(orderId));
    }
}
