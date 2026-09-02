package com.xt.xiaoxingxing.playground.features.xxljob.entity;

import com.xt.xiaoxingxing.playground.features.xxljob.enums.XxlWorkItemStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 分片工作项。 */
@Data
public class XxlWorkItem {
    private Long id;
    private Long batchId;
    private Integer itemNo;
    private Integer bucketNo;
    private Integer plannedFailures;
    private XxlWorkItemStatus status;
    private Integer attemptCount;
    private LocalDateTime availableAt;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Long lastLogId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
