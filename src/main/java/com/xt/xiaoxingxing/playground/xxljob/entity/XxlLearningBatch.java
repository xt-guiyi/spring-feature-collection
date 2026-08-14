package com.xt.xiaoxingxing.playground.xxljob.entity;

import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningBatchStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 一组可由固定逻辑桶分片处理的工作项。 */
@Data
public class XxlLearningBatch {
    private Long id;
    private String batchKey;
    private Integer itemCount;
    private Integer failEvery;
    private Integer failTimes;
    private XxlLearningBatchStatus status;
    private Long generatedExecutionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
