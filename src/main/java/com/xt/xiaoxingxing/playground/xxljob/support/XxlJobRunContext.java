package com.xt.xiaoxingxing.playground.xxljob.support;

import com.xxl.job.core.context.XxlJobHelper;
import lombok.Getter;

/**
 * 当前 XXL-JOB 调度的不可变运行上下文。
 *
 * <p>业务服务不直接依赖 {@link XxlJobHelper} 的线程本地静态 API，而由 Handler 在入口处截取一次上下文后显式传入。
 * 这样既便于理解一次调度的身份，也能避免异步业务代码在 Handler 返回后读取到已失效或错误的线程上下文。</p>
 */
@Getter
public final class XxlJobRunContext {

    private final long jobId;
    private final long logId;
    private final long logDateTime;
    private final String logFileName;
    private final int shardIndex;
    private final int shardTotal;

    public XxlJobRunContext(
            long jobId,
            long logId,
            long logDateTime,
            String logFileName,
            int shardIndex,
            int shardTotal
    ) {
        this.jobId = jobId;
        this.logId = logId;
        this.logDateTime = logDateTime;
        this.logFileName = logFileName;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
    }

    /** 在 Handler 线程中截取当前调度上下文；不要在 Handler 返回后的异步线程中重新调用。 */
    public static XxlJobRunContext current() {
        return new XxlJobRunContext(
                XxlJobHelper.getJobId(),
                XxlJobHelper.getLogId(),
                XxlJobHelper.getLogDateTime(),
                XxlJobHelper.getLogFileName(),
                XxlJobHelper.getShardIndex(),
                XxlJobHelper.getShardTotal()
        );
    }
}
