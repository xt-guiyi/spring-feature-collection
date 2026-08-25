package com.xt.xiaoxingxing.playground.xxljob.support;

import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.Getter;

/** 当前 XXL-JOB 调度的不可变上下文。 */
@Getter
public final class XxlJobRunContext {

    private final long jobId;
    private final long logId;
    private final long logDateTime;
    private final int shardIndex;
    private final int shardTotal;

    private XxlJobRunContext(
            long jobId,
            long logId,
            long logDateTime,
            int shardIndex,
            int shardTotal
    ) {
        BusinessAssert.isTrue(shardTotal > 0, "shardTotal必须大于0");
        BusinessAssert.isTrue(shardIndex >= 0 && shardIndex < shardTotal,
                "shardIndex必须位于[0, shardTotal)范围内");
        this.jobId = jobId;
        this.logId = logId;
        this.logDateTime = logDateTime;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
    }

    /** 在 Handler 线程中读取当前调度上下文。 */
    public static XxlJobRunContext current() {
        return new XxlJobRunContext(
                XxlJobHelper.getJobId(),
                XxlJobHelper.getLogId(),
                XxlJobHelper.getLogDateTime(),
                XxlJobHelper.getShardIndex(),
                XxlJobHelper.getShardTotal()
        );
    }
}
