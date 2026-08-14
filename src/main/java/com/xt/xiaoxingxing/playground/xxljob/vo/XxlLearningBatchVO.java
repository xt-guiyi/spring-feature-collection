package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import lombok.Data;

import java.time.LocalDateTime;

/** 批处理批次响应；批次状态由数据库中的全部工作项推导，而不是由单个 Executor 猜测。 */
@Data
public class XxlLearningBatchVO {

    private Long id;
    private String batchKey;
    private Integer itemCount;
    /** 每隔多少个工作项安排一次教学用的计划失败；0 表示不安排。 */
    private Integer failEvery;
    /** 命中计划失败的工作项在成功前要失败的次数。 */
    private Integer failTimes;
    private String status;
    private Long generatedExecutionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public static XxlLearningBatchVO from(XxlLearningBatch source) {
        if (source == null) {
            return null;
        }
        XxlLearningBatchVO target = new XxlLearningBatchVO();
        target.setId(source.getId());
        target.setBatchKey(source.getBatchKey());
        target.setItemCount(source.getItemCount());
        target.setFailEvery(source.getFailEvery());
        target.setFailTimes(source.getFailTimes());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().getValue());
        target.setGeneratedExecutionId(source.getGeneratedExecutionId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setCompletedAt(source.getCompletedAt());
        return target;
    }
}
