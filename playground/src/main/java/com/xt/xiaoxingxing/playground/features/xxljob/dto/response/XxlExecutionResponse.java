package com.xt.xiaoxingxing.playground.features.xxljob.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** 执行台账响应。 */
@Data
public class XxlExecutionResponse {

    private Long id;
    /** 业务幂等键。 */
    private String executionKey;
    private String handlerName;
    private String status;
    /** 成功取得租约的次数。 */
    private Integer attemptCount;
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
