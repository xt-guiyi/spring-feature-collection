package com.xt.xiaoxingxing.playground.xxljob.handler;

import com.xt.xiaoxingxing.playground.xxljob.config.XxlJobNames;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.RetryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.service.XxlJobLearningService;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobHandlerSupport;
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
    private final XxlJobLearningService learningService;

    public XxlJobBusinessHandler(
            XxlJobHandlerSupport handlerSupport,
            XxlJobLearningService learningService
    ) {
        this.handlerSupport = handlerSupport;
        this.learningService = learningService;
    }

    /** 演示失败重试与业务幂等。 */
    @XxlJob(XxlJobNames.RETRY)
    public void xxlRetryJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.RETRY, context -> {
            RetryJobParam param = handlerSupport.parseParam(RetryJobParam.class);
            String result = learningService.runRetry(param, context);
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

            String result = learningService.runDailyOrderSummary(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /** 幂等生成分片工作批次。 */
    @XxlJob(XxlJobNames.GENERATE_WORK_BATCH)
    public void xxlGenerateWorkBatchJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.GENERATE_WORK_BATCH, context -> {
            GenerateWorkBatchJobParam param = handlerSupport.parseParam(GenerateWorkBatchJobParam.class);
            String result = learningService.generateWorkBatch(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /** 分片领取并处理工作项。 */
    @XxlJob(XxlJobNames.PROCESS_WORK_ITEMS)
    public void xxlProcessWorkItemsJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.PROCESS_WORK_ITEMS, context -> {
            ProcessWorkItemsJobParam param = handlerSupport.parseParam(ProcessWorkItemsJobParam.class);
            String result = learningService.processWorkItems(param, context);
            handlerSupport.handleSuccess(result);
        });
    }
}
