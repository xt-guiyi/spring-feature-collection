package com.xt.xiaoxingxing.playground.rabbitmq.controller;

import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitAckDemoRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitOrderingDemoRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitRetryDemoRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitRoutingMessageRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitStreamEventRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitTextMessageRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.enums.RabbitAckAction;
import com.xt.xiaoxingxing.playground.rabbitmq.service.RabbitMqDemoService;
import com.xt.xiaoxingxing.playground.rabbitmq.vo.RabbitMessagePublishVO;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RabbitMQ 路由、确认、重试、顺序和 Stream 的基础学习入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rabbitmq")
public class RabbitMqDemoController {

    private final RabbitMqDemoService demoService;

    /** Direct Exchange：Routing Key 必须完全等于 Binding Key 才会进入队列。 */
    @PostMapping("/demo/direct")
    public Result<RabbitMessagePublishVO> direct(@Valid @RequestBody RabbitRoutingMessageRequest request) {
        return Result.ok(demoService.sendDirect(request));
    }

    /** Topic Exchange：推荐尝试 demo.order.created、demo.order.paid 和 demo.user.paid。 */
    @PostMapping("/demo/topic")
    public Result<RabbitMessagePublishVO> topic(@Valid @RequestBody RabbitRoutingMessageRequest request) {
        return Result.ok(demoService.sendTopic(request));
    }

    /** Fanout Exchange：同一条消息会分别复制到 queue.a 和 queue.b。 */
    @PostMapping("/demo/fanout")
    public Result<RabbitMessagePublishVO> fanout(@Valid @RequestBody RabbitTextMessageRequest request) {
        return Result.ok(demoService.sendFanout(request.getMessage()));
    }

    /** Mandatory Return：故意使用没有 Binding 的 Routing Key，预期 success=false。 */
    @PostMapping("/demo/mandatory-return")
    public Result<RabbitMessagePublishVO> mandatoryReturn(@Valid @RequestBody RabbitTextMessageRequest request) {
        return Result.ok(demoService.sendMandatoryReturn(request.getMessage()));
    }

    /** 通过 action 分别观察 basicAck、basicNack 和 basicReject。 */
    @PostMapping("/demo/ack")
    public Result<RabbitMessagePublishVO> ack(@Valid @RequestBody RabbitAckDemoRequest request) {
        return Result.ok(demoService.sendAckDemo(request));
    }

    /** 省去 action 字段的专用重试入口；failTimes 大于3时默认会进入最终 DLQ。 */
    @PostMapping("/demo/retry")
    public Result<RabbitMessagePublishVO> retry(@Valid @RequestBody RabbitRetryDemoRequest request) {
        RabbitAckDemoRequest ackRequest = new RabbitAckDemoRequest();
        ackRequest.setMessage(request.getMessage());
        ackRequest.setFailTimes(request.getFailTimes());
        ackRequest.setAction(RabbitAckAction.RETRY_THEN_SUCCESS);
        return Result.ok(demoService.sendAckDemo(ackRequest));
    }

    /** 依次发送 1..count，通过管理界面和日志观察 Single Active Consumer。 */
    @PostMapping("/demo/ordering")
    public Result<List<RabbitMessagePublishVO>> ordering(@Valid @RequestBody RabbitOrderingDemoRequest request) {
        return Result.ok(demoService.sendOrdered(request));
    }

    /** 使用原生 5552 Stream 协议追加一条可按 offset 回放的事件。 */
    @PostMapping("/stream/events")
    public Result<RabbitMessagePublishVO> stream(@Valid @RequestBody RabbitStreamEventRequest request) {
        return Result.ok(demoService.sendStreamEvent(request));
    }
}
