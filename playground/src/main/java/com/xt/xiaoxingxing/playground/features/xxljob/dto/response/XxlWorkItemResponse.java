package com.xt.xiaoxingxing.playground.features.xxljob.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作项响应。 */
@Data
public class XxlWorkItemResponse {

    private Long id;
    private Long batchId;
    private Integer itemNo;
    /** 逻辑桶编号。 */
    private Integer bucketNo;
    private Integer plannedFailures;
    private String status;
    private Integer attemptCount;
    private LocalDateTime availableAt;
    private LocalDateTime leaseExpiresAt;
    private Long lastLogId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

}
