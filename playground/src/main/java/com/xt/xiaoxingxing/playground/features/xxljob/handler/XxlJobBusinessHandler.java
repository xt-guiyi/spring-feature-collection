package com.xt.xiaoxingxing.playground.features.xxljob.handler;

import com.xt.xiaoxingxing.playground.features.xxljob.constants.XxlJobNames;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.RetryJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.service.XxlJobService;
import com.xt.xiaoxingxing.playground.features.xxljob.support.XxlJobHandlerSupport;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/** XXL-JOB 业务任务入口。 */
@Component
public class XxlJobBusinessHandler {

    /** 订单业务时区。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final XxlJobHandlerSupport handlerSupport;
    private final XxlJobService xxlJobService;

    public XxlJobBusinessHandler(
            XxlJobHandlerSupport handlerSupport,
            XxlJobService xxlJobService
    ) {
        this.handlerSupport = handlerSupport;
        this.xxlJobService = xxlJobService;
    }

    /** 演示失败重试与业务幂等。 */
    @XxlJob(XxlJobNames.RETRY)
    public void xxlRetryJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.RETRY, context -> {
            RetryJobParam param = handlerSupport.parseParam(RetryJobParam.class);
            String result = xxlJobService.runRetry(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /** 生成可按版本重算的每日订单汇总。 */
    @XxlJob(XxlJobNames.DAILY_ORDER_SUMMARY)
    public void xxlDailyOrderSummaryJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.DAILY_ORDER_SUMMARY, context -> {
            DailyOrderSummaryJobParam param = handlerSupport.parseParam(DailyOrderSummaryJobParam.class);

            // 未指定日期时汇总调度日前一天的订单。
            if (param.getBusinessDate() == null) {
                param.setBusinessDate(
                        Instant.ofEpochMilli(context.logDateTime())
                                .atZone(BUSINESS_ZONE)
                                .toLocalDate()
                                .minusDays(1)
                );
            }

            String result = xxlJobService.runDailyOrderSummary(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /** 幂等生成分片工作批次。 */
    @XxlJob(XxlJobNames.GENERATE_WORK_BATCH)
    public void xxlGenerateWorkBatchJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.GENERATE_WORK_BATCH, context -> {
            GenerateWorkBatchJobParam param = handlerSupport.parseParam(GenerateWorkBatchJobParam.class);
            String result = xxlJobService.generateWorkBatch(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /** 分片领取并处理工作项。 */
    @XxlJob(XxlJobNames.PROCESS_WORK_ITEMS)
    public void xxlProcessWorkItemsJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.PROCESS_WORK_ITEMS, context -> {
            ProcessWorkItemsJobParam param = handlerSupport.parseParam(ProcessWorkItemsJobParam.class);
            String result = xxlJobService.processWorkItems(param, context);
            handlerSupport.handleSuccess(result);
        });
    }
}
