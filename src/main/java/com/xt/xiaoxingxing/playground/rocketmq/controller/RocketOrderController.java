package com.xt.xiaoxingxing.playground.rocketmq.controller;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.rocketmq.service.RocketOrderApplicationService;
import com.xt.xiaoxingxing.playground.rocketmq.service.RocketTransactionMessageService;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketOrderCreateVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketTransactionOrderVO;
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

/** 两种可靠下单机制的独立入口；响应 mechanism 明确标识本次选择。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/orders")
public class RocketOrderController {

    private final RocketOrderApplicationService orderApplicationService;
    private final RocketTransactionMessageService transactionMessageService;

    /** 订单、库存和两条 Outbox 在同一事务提交；随后从 operations 与 Dashboard 观察异步发布。 */
    @PostMapping("/outbox")
    public Result<RocketOrderCreateVO> createOutbox(
            @Valid @RequestBody CompleteOrderCreateRequest request) {
        return Result.ok(orderApplicationService.createOutboxOrder(request));
    }

    /** PREPARED → 半消息 → 本地订单事务 → commit/rollback；不写 Outbox 创建事件。 */
    @PostMapping("/transaction-message")
    public Result<RocketTransactionOrderVO> createTransactionMessage(
            @Valid @RequestBody CompleteOrderCreateRequest request) {
        return Result.ok(transactionMessageService.createTransactionOrder(request));
    }

    /** PENDING → PAID 条件更新和 ORDER_PAID Outbox 同事务提交，与超时取消并发时只能一方成功。 */
    @PostMapping("/{orderId}/pay")
    public Result<Boolean> pay(@PathVariable @Positive(message = "orderId必须大于0") Long orderId) {
        return Result.ok(orderApplicationService.payOrder(orderId));
    }
}
