package com.xt.xiaoxingxing.playground.rabbitmq.controller;

import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqNotificationLog;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOrderStatistics;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rabbitmq.service.OutboxEventService;
import com.xt.xiaoxingxing.playground.rabbitmq.service.RabbitMqOperationsService;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 从 HTTP 观察 Outbox、消费幂等、统计与通知日志。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rabbitmq")
public class RabbitMqOperationsController {

    private final OutboxEventService outboxEventService;
    private final RabbitMqOperationsService operationsService;

    @GetMapping("/outbox-events")
    public Result<PageResult<MqOutboxEvent>> outboxEvents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum至少为1") int pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize至少为1")
            @Max(value = 100, message = "pageSize最多为100") int pageSize) {
        return Result.ok(outboxEventService.page(status, pageNum, pageSize));
    }

    @GetMapping("/outbox-events/{id}")
    public Result<MqOutboxEvent> outboxEvent(@PathVariable String id) {
        return Result.ok(outboxEventService.getById(id));
    }

    @GetMapping("/consumed-messages")
    public Result<PageResult<MqConsumedMessage>> consumedMessages(
            @RequestParam(required = false) String consumerName,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum至少为1") int pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize至少为1")
            @Max(value = 100, message = "pageSize最多为100") int pageSize) {
        return Result.ok(operationsService.pageConsumed(consumerName, pageNum, pageSize));
    }

    @GetMapping("/order-statistics")
    public Result<MqOrderStatistics> orderStatistics() {
        return Result.ok(operationsService.getStatistics());
    }

    @GetMapping("/notification-logs")
    public Result<PageResult<MqNotificationLog>> notificationLogs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "pageNum至少为1") int pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize至少为1")
            @Max(value = 100, message = "pageSize最多为100") int pageSize) {
        return Result.ok(operationsService.pageNotifications(pageNum, pageSize));
    }
}
