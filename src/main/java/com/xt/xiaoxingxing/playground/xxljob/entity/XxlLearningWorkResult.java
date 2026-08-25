package com.xt.xiaoxingxing.playground.xxljob.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作项处理结果。 */
@Data
public class XxlLearningWorkResult {
    private Long id;
    private Long workItemId;
    private Long batchId;
    private Integer itemNo;
    private Long executionId;
    private String resultValue;
    private Long jobId;
    private Long logId;
    private Integer shardIndex;
    private Integer shardTotal;
    private LocalDateTime createdAt;
}
