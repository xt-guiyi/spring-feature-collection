package com.xt.xiaoxingxing.playground.features.xxljob.entity;

import com.xt.xiaoxingxing.playground.features.xxljob.enums.XxlBatchStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 分片工作批次。 */
@Data
public class XxlBatch {
    private Long id;
    private String batchKey;
    private Integer itemCount;
    private Integer failEvery;
    private Integer failTimes;
    private XxlBatchStatus status;
    private Long generatedExecutionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
