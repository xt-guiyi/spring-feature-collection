package com.xt.xiaoxingxing.playground.features.xxljob.support;

import com.xxl.job.core.context.XxlJobHelper;

/** 当前 XXL-JOB 调度的不可变上下文。 */
public record XxlJobRunContext(
        long jobId,
        long logId,
        long logDateTime,
        int shardIndex,
        int shardTotal
) {

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
