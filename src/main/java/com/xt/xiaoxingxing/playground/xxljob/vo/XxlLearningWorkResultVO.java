package com.xt.xiaoxingxing.playground.xxljob.vo;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkResult;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作项成功副作用的审计响应；一项工作最多只能生成一条结果。 */
@Data
public class XxlLearningWorkResultVO {

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

    public static XxlLearningWorkResultVO from(XxlLearningWorkResult source) {
        if (source == null) {
            return null;
        }
        XxlLearningWorkResultVO target = new XxlLearningWorkResultVO();
        target.setId(source.getId());
        target.setWorkItemId(source.getWorkItemId());
        target.setBatchId(source.getBatchId());
        target.setItemNo(source.getItemNo());
        target.setExecutionId(source.getExecutionId());
        target.setResultValue(source.getResultValue());
        target.setJobId(source.getJobId());
        target.setLogId(source.getLogId());
        target.setShardIndex(source.getShardIndex());
        target.setShardTotal(source.getShardTotal());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }
}
