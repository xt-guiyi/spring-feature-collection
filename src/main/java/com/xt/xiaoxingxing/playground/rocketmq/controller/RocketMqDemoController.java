package com.xt.xiaoxingxing.playground.rocketmq.controller;

import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketBatchMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketDelayMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketFifoMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketRetryMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketTextMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.service.RocketMqDemoService;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketMessagePublishVO;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 基础消息入口；发送后可同时观察应用日志、Dashboard 的消息 Key 与各 ConsumerGroup 进度。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/demo")
public class RocketMqDemoController {

    private final RocketMqDemoService demoService;

    /** 同步等待 Broker SendReceipt，success 不代表消费者业务已经完成。 */
    @PostMapping("/normal")
    public Result<RocketMessagePublishVO> normal(@Valid @RequestBody RocketTextMessageRequest request) {
        return Result.ok(demoService.sendNormal(request));
    }

    /** 立即返回客户端 accepted；Broker ID 或异常在异步回调日志中观察。 */
    @PostMapping("/async")
    public Result<RocketMessagePublishVO> async(@Valid @RequestBody RocketTextMessageRequest request) {
        return Result.ok(demoService.sendAsync(request));
    }

    /** 修改 Tag 后在 Dashboard 对照订阅过滤；DEMO 会被基础监听器消费。 */
    @PostMapping("/tag")
    public Result<RocketMessagePublishVO> tag(@Valid @RequestBody RocketTextMessageRequest request) {
        return Result.ok(demoService.sendTag(request));
    }

    /** 同一 DEMO 消息会被审计组和通知组各消费一份。 */
    @PostMapping("/multi-group")
    public Result<RocketMessagePublishVO> multiGroup(@Valid @RequestBody RocketTextMessageRequest request) {
        return Result.ok(demoService.sendMultiGroup(request));
    }

    /** 观察相同 MessageGroup 的 sequence；不同 businessKey 之间不保证全局顺序。 */
    @PostMapping("/fifo")
    public Result<List<RocketMessagePublishVO>> fifo(@Valid @RequestBody RocketFifoMessageRequest request) {
        return Result.ok(demoService.sendFifo(request));
    }

    /** 在 DELAY Topic 观察期望投递时间、实际接收时间和漂移。 */
    @PostMapping("/delay")
    public Result<RocketMessagePublishVO> delay(@Valid @RequestBody RocketDelayMessageRequest request) {
        return Result.ok(demoService.sendDelay(request));
    }

    /** failTimes 超过 Broker 重试上限时，在对应 ConsumerGroup 的 DLQ 观察消息。 */
    @PostMapping("/retry")
    public Result<RocketMessagePublishVO> retry(@Valid @RequestBody RocketRetryMessageRequest request) {
        return Result.ok(demoService.sendRetry(request));
    }

    /** HTTP 应用层批量，返回每条独立结果，不承诺一次 Broker 批量帧。 */
    @PostMapping("/batch")
    public Result<List<RocketMessagePublishVO>> batch(@Valid @RequestBody RocketBatchMessageRequest request) {
        return Result.ok(demoService.sendBatch(request));
    }
}
