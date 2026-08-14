package com.xt.xiaoxingxing.playground.xxljob.handler;

import com.xt.xiaoxingxing.playground.xxljob.config.XxlJobProperties;
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

/**
 * 将 XXL-JOB 调度入口适配到可持久化、可幂等的学习业务服务。
 *
 * <p>Handler 只负责协议层工作：解析参数、截取调度上下文、调用一次同步业务方法并回报结果。
 * 数据库抢占、条件更新、幂等和失败恢复都留在 {@link XxlJobLearningService}，避免调度框架语义污染业务事务。</p>
 */
@Component
public class XxlJobBusinessHandler {

    private final XxlJobHandlerSupport handlerSupport;
    private final XxlJobLearningService learningService;
    /**
     * 从 {@code xxl.job.learning.business-zone} 绑定而来。
     *
     * <p>时区属于部署运行配置，而不是任务处理协议：例如海外部署仍可按中国业务日汇总，或改为当地业务日，
     * 无需重新编译代码。</p>
     */
    private final ZoneId businessZone;

    public XxlJobBusinessHandler(
            XxlJobHandlerSupport handlerSupport,
            XxlJobLearningService learningService,
            XxlJobProperties properties
    ) {
        this.handlerSupport = handlerSupport;
        this.learningService = learningService;
        this.businessZone = properties.getLearning().getBusinessZone();
    }

    /**
     * 演示“框架重试不等于业务恰好一次”。
     *
     * <ol>
     *     <li>解析稳定 businessKey、预期失败次数和执行权租约；</li>
     *     <li>把参数与当前 logId 一并交给服务，由持久记录裁决本次是执行、重复成功还是仍需失败；</li>
     *     <li>仅在服务完整返回后标记成功；异常由统一入口记录并抛回 XXL-JOB 触发重试。</li>
     * </ol>
     */
    @XxlJob("xxlRetryJobHandler")
    public void xxlRetryJobHandler() throws Exception {
        handlerSupport.execute("xxlRetryJobHandler", context -> {
            RetryJobParam param = handlerSupport.parseParam(RetryJobParam.class);
            String result = learningService.runRetry(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /**
     * 生成每日订单汇总，支持同一天按 runVersion 受控重算。
     *
     * <ol>
     *     <li>解析版本与租约；businessDate 可以由 Admin 显式传入；</li>
     *     <li>未传日期时，根据本次调度的原始 logDateTime，按 YAML 声明的业务时区取前一天，而不是读取当前系统日期；</li>
     *     <li>将确定后的日期交给服务；需要跨午夜严格重跑时，应在任务参数中显式固定 businessDate。</li>
     * </ol>
     */
    @XxlJob("xxlDailyOrderSummaryJobHandler")
    public void xxlDailyOrderSummaryJobHandler() throws Exception {
        handlerSupport.execute("xxlDailyOrderSummaryJobHandler", context -> {
            DailyOrderSummaryJobParam param = handlerSupport.parseParam(DailyOrderSummaryJobParam.class);
            if (param.getBusinessDate() == null) {
                param.setBusinessDate(
                        Instant.ofEpochMilli(context.getLogDateTime())
                                .atZone(businessZone)
                                .toLocalDate()
                                .minusDays(1)
                );
            }

            String result = learningService.runDailyOrderSummary(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /**
     * 幂等生成一个工作批次，为后续分片广播准备持久化工作项。
     *
     * <ol>
     *     <li>校验 batchKey、工作项数量和故障注入参数；</li>
     *     <li>服务通过批次唯一键收敛人工重跑或重复调度，不能在 Handler 内仅凭内存去重；</li>
     *     <li>事务提交并返回摘要后才向 Admin 回报成功。</li>
     * </ol>
     */
    @XxlJob("xxlGenerateWorkBatchJobHandler")
    public void xxlGenerateWorkBatchJobHandler() throws Exception {
        handlerSupport.execute("xxlGenerateWorkBatchJobHandler", context -> {
            GenerateWorkBatchJobParam param = handlerSupport.parseParam(GenerateWorkBatchJobParam.class);
            param.setBatchKey(param.getBatchKey().trim());
            String result = learningService.generateWorkBatch(param, context);
            handlerSupport.handleSuccess(result);
        });
    }

    /**
     * 分片领取并处理工作项，演示扩缩容、租约和逐项幂等的组合。
     *
     * <ol>
     *     <li>参数限制单轮批量、最大尝试、租约与失败退避，防止一个 JobThread 无界工作；</li>
     *     <li>上下文携带 shardIndex/shardTotal，服务以数据库条件更新领取当前分片可处理的工作项；</li>
     *     <li>部分工作项失败必须持久化尝试结果；整轮异常可以重试，但已完成项不得再次产生副作用。</li>
     * </ol>
     */
    @XxlJob("xxlProcessWorkItemsJobHandler")
    public void xxlProcessWorkItemsJobHandler() throws Exception {
        handlerSupport.execute("xxlProcessWorkItemsJobHandler", context -> {
            ProcessWorkItemsJobParam param = handlerSupport.parseParam(ProcessWorkItemsJobParam.class);
            param.setBatchKey(param.getBatchKey().trim());
            String result = learningService.processWorkItems(param, context);
            handlerSupport.handleSuccess(result);
        });
    }
}
