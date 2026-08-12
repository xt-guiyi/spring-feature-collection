package com.xt.xiaoxingxing.playground.rocketmq.controller;

import com.xt.xiaoxingxing.playground.rocketmq.service.RocketMqOperationsService;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketConsumedMessageVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketNotificationLogVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketOrderStatisticsVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketOutboxEventVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketTransactionRecordVO;
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

/** 只读运维观察入口；用于把日志、PostgreSQL 状态与 Dashboard 消息关联起来。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/operations")
public class RocketMqOperationsController {

    private final RocketMqOperationsService operationsService;

    @GetMapping("/outbox-events")
    public Result<PageResult<RocketOutboxEventVO>> outboxEvents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageOutbox(status, pageNum, pageSize));
    }

    @GetMapping("/outbox-events/{id}")
    public Result<RocketOutboxEventVO> outboxEvent(@PathVariable String id) {
        return Result.ok(operationsService.getOutbox(id));
    }

    @GetMapping("/transaction-records")
    public Result<PageResult<RocketTransactionRecordVO>> transactionRecords(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageTransactions(status, pageNum, pageSize));
    }

    @GetMapping("/transaction-records/{transactionId}")
    public Result<RocketTransactionRecordVO> transactionRecord(@PathVariable String transactionId) {
        return Result.ok(operationsService.getTransaction(transactionId));
    }

    @GetMapping("/consumed-messages")
    public Result<PageResult<RocketConsumedMessageVO>> consumedMessages(
            @RequestParam(required = false) String consumerName,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageConsumed(consumerName, pageNum, pageSize));
    }

    @GetMapping("/order-statistics")
    public Result<RocketOrderStatisticsVO> orderStatistics() {
        return Result.ok(operationsService.getStatistics());
    }

    @GetMapping("/notification-logs")
    public Result<PageResult<RocketNotificationLogVO>> notificationLogs(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageNotifications(pageNum, pageSize));
    }
}
