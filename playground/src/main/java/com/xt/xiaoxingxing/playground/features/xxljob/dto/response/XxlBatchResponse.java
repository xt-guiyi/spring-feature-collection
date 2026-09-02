package com.xt.xiaoxingxing.playground.features.xxljob.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作批次响应。 */
@Data
public class XxlBatchResponse {

    private Long id;
    private String batchKey;
    private Integer itemCount;
    /** 计划失败间隔，0 表示禁用。 */
    private Integer failEvery;
    /** 计划失败次数。 */
    private Integer failTimes;
    private String status;
    private Long generatedExecutionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

}
