package com.xt.xiaoxingxing.playground.xxljob.entity;

import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningWorkItemStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 可独立领取、失败、重试并最终落结果的一项工作。 */
@Data
public class XxlLearningWorkItem {
    private Long id;
    private Long batchId;
    private Integer itemNo;
    private Integer bucketNo;
    private Integer plannedFailures;
    private XxlLearningWorkItemStatus status;
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
