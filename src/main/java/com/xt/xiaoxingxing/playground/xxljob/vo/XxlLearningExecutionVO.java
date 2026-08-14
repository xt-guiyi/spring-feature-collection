package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * XXL-JOB 学习任务的业务执行台账响应。
 *
 * <p>这里没有直接把 {@link XxlLearningExecution} 返回给前端。实体属于数据库映射模型，
 * 以后即使增加内部锁字段，对外接口也不会被迫跟着变化；这正是 VO 与实体分层的意义。</p>
 */
@Data
public class XxlLearningExecutionVO {

    private Long id;
    /** 稳定业务幂等键；同一业务执行链重试时不会改变。 */
    private String executionKey;
    private String handlerName;
    private String status;
    /** 已经成功取得执行租约的次数，而不是 Admin 页面上的简单点击次数。 */
    private Integer attemptCount;
    /** 当前执行者持有的租约令牌；成功或失败收口后会清空。 */
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Long jobId;
    private Long logId;
    private Long logDateTime;
    private String logFileName;
    private Integer shardIndex;
    private Integer shardTotal;
    private String resultMessage;
    private String lastError;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 在 Service 边界完成实体到 HTTP 响应模型的逐字段转换。 */
    public static XxlLearningExecutionVO from(XxlLearningExecution source) {
        if (source == null) {
            return null;
        }
        XxlLearningExecutionVO target = new XxlLearningExecutionVO();
        target.setId(source.getId());
        target.setExecutionKey(source.getExecutionKey());
        target.setHandlerName(source.getHandlerName());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().getValue());
        target.setAttemptCount(source.getAttemptCount());
        target.setLeaseToken(source.getLeaseToken());
        target.setLeaseExpiresAt(source.getLeaseExpiresAt());
        target.setJobId(source.getJobId());
        target.setLogId(source.getLogId());
        target.setLogDateTime(source.getLogDateTime());
        target.setLogFileName(source.getLogFileName());
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
