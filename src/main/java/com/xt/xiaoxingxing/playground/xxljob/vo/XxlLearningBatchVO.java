package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作批次响应。 */
@Data
public class XxlLearningBatchVO {

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

    public static XxlLearningBatchVO from(XxlLearningBatch source) {
        XxlLearningBatchVO target = new XxlLearningBatchVO();
        target.setId(source.getId());
        target.setBatchKey(source.getBatchKey());
        target.setItemCount(source.getItemCount());
        target.setFailEvery(source.getFailEvery());
        target.setFailTimes(source.getFailTimes());
        target.setStatus(source.getStatus().name());
        target.setGeneratedExecutionId(source.getGeneratedExecutionId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setCompletedAt(source.getCompletedAt());
        return target;
    }
}
