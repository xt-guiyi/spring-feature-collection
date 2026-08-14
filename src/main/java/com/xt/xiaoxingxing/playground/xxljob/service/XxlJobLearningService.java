package com.xt.xiaoxingxing.playground.xxljob.service;

import com.xt.xiaoxingxing.playground.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.RetryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobRunContext;

/**
 * XXL-JOB 学习案例的业务入口。
 *
 * <p>Handler 只负责调度协议适配；执行幂等、租约、分片领取和数据库终态均由本服务实现。</p>
 */
public interface XxlJobLearningService {

    String runRetry(RetryJobParam param, XxlJobRunContext context);

    String runDailyOrderSummary(DailyOrderSummaryJobParam param, XxlJobRunContext context);

    String generateWorkBatch(GenerateWorkBatchJobParam param, XxlJobRunContext context);

    String processWorkItems(ProcessWorkItemsJobParam param, XxlJobRunContext context);
}
