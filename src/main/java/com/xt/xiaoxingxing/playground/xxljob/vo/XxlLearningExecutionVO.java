package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import lombok.Data;

import java.time.LocalDateTime;

/** 执行台账响应。 */
@Data
public class XxlLearningExecutionVO {

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

    /** 转换执行台账。 */
    public static XxlLearningExecutionVO from(XxlLearningExecution source) {
        XxlLearningExecutionVO target = new XxlLearningExecutionVO();
        target.setId(source.getId());
        target.setExecutionKey(source.getExecutionKey());
        target.setHandlerName(source.getHandlerName());
        target.setStatus(source.getStatus().name());
        target.setAttemptCount(source.getAttemptCount());
        target.setLeaseExpiresAt(source.getLeaseExpiresAt());
        target.setJobId(source.getJobId());
        target.setLogId(source.getLogId());
        target.setLogDateTime(source.getLogDateTime());
        target.setShardIndex(source.getShardIndex());
        target.setShardTotal(source.getShardTotal());
        target.setResultMessage(source.getResultMessage());
        target.setLastError(source.getLastError());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
