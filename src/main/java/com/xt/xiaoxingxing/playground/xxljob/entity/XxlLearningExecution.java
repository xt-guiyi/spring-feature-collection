package com.xt.xiaoxingxing.playground.xxljob.entity;

import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningExecutionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 可重试执行台账。 */
@Data
public class XxlLearningExecution {
    private Long id;
    private String executionKey;
    private String handlerName;
    private XxlLearningExecutionStatus status;
    private Integer attemptCount;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Long jobId;
    private Long logId;
    private Long logDateTime;
    private Integer shardIndex;
    private Integer shardTotal;
    private String resultMessage;
    private String lastError;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
