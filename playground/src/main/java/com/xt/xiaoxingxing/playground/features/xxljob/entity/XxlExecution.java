package com.xt.xiaoxingxing.playground.features.xxljob.entity;

import com.xt.xiaoxingxing.playground.features.xxljob.enums.XxlExecutionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 可重试执行台账。 */
@Data
public class XxlExecution {
    private Long id;
    private String executionKey;
    private String handlerName;
    private XxlExecutionStatus status;
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
