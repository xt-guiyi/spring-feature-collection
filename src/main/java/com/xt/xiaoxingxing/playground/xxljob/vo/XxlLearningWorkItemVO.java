package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkItem;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作项响应，用于观察逻辑桶分片、领取租约、失败等待与最终收口。 */
@Data
public class XxlLearningWorkItemVO {

    private Long id;
    private Long batchId;
    private Integer itemNo;
    /** 固定逻辑桶编号；运行时再通过 bucketNo % shardTotal 分配给当前分片。 */
    private Integer bucketNo;
    private Integer plannedFailures;
    private String status;
    private Integer attemptCount;
    private LocalDateTime availableAt;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Long lastLogId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public static XxlLearningWorkItemVO from(XxlLearningWorkItem source) {
        if (source == null) {
            return null;
        }
        XxlLearningWorkItemVO target = new XxlLearningWorkItemVO();
        target.setId(source.getId());
        target.setBatchId(source.getBatchId());
        target.setItemNo(source.getItemNo());
        target.setBucketNo(source.getBucketNo());
        target.setPlannedFailures(source.getPlannedFailures());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().getValue());
        target.setAttemptCount(source.getAttemptCount());
        target.setAvailableAt(source.getAvailableAt());
        target.setLeaseToken(source.getLeaseToken());
        target.setLeaseExpiresAt(source.getLeaseExpiresAt());
        target.setLastLogId(source.getLastLogId());
        target.setLastError(source.getLastError());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setCompletedAt(source.getCompletedAt());
        return target;
    }
}
