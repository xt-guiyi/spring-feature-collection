package com.xt.xiaoxingxing.playground.features.xxljob.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** 工作结果响应。 */
@Data
public class XxlWorkResultResponse {

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
