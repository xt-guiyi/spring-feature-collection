package com.xt.xiaoxingxing.playground.xxljob.service;

import com.xt.xiaoxingxing.playground.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.RetryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkItem;
import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningBatchStatus;
import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningExecutionStatus;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobRunContext;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * XXL-JOB 学习业务的步骤编排层。
 *
 * <p>复杂任务先把步骤写清楚，再调用 {@link XxlJobTransactionService} 的短事务逐步完成。
 * 本类故意不加一个覆盖整段方法的大事务：任务可能需要故意抛错交给 Admin 重试，而已经提交的尝试次数、
 * 失败状态和前面成功的工作项必须保留下来。</p>
 */
@Service
@RequiredArgsConstructor
public class XxlJobLearningServiceImpl implements XxlJobLearningService {

    private static final String RETRY_HANDLER = "xxlRetryJobHandler";
    private static final String SUMMARY_HANDLER = "xxlDailyOrderSummaryJobHandler";
    private static final String GENERATE_HANDLER = "xxlGenerateWorkBatchJobHandler";
    private static final String PROCESS_HANDLER = "xxlProcessWorkItemsJobHandler";

    private final XxlJobTransactionService transactionService;

    /**
     * 失败重试案例。
     *
     * <ol>
     *     <li>用稳定 businessKey 原子取得执行租约，attemptCount 只在真正取得租约时增加；</li>
     *     <li>已成功则幂等返回，活跃租约则拒绝并发重复；</li>
     *     <li>当前尝试序号不大于 failTimes 时，先独立提交 FAILED，再抛异常让 Admin 重试；</li>
     *     <li>超过计划失败次数后，凭租约条件写 SUCCESS。</li>
     * </ol>
     */
    @Override
    public String runRetry(RetryJobParam param, XxlJobRunContext context) {
        param.setBusinessKey(param.getBusinessKey().trim());
        String executionKey = "retry:" + param.getBusinessKey();
        XxlLearningExecution execution = claimOrRead(
                executionKey, RETRY_HANDLER, param.getLeaseSeconds(), context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "业务键已经成功，本次幂等跳过，attemptCount=" + execution.getAttemptCount();
        }

        if (execution.getAttemptCount() <= param.getFailTimes()) {
            String error = "学习案例按计划失败：attempt=" + execution.getAttemptCount()
                    + "，failTimes=" + param.getFailTimes();
            transactionService.failExecution(execution, error);
            // 只有把异常继续交还 XXL-JOB，Admin 配置的失败重试次数才会生效。
            throw new IllegalStateException(error);
        }

        String result = "重试任务成功，businessKey=" + param.getBusinessKey()
                + "，attemptCount=" + execution.getAttemptCount();
        transactionService.succeedExecution(execution, result);
        return result;
    }

    /**
     * 每日订单汇总。
     *
     * <ol>
     *     <li>以业务日期和 runVersion 组成稳定幂等键；</li>
     *     <li>取得租约后，在一个短事务内扫描 orders 的半开时间区间；</li>
     *     <li>只允许更高版本覆盖，并把日报写入与执行 SUCCESS 一起提交；</li>
     *     <li>失败时尽力留下 FAILED，随后继续抛出原始异常。</li>
     * </ol>
     */
    @Override
    public String runDailyOrderSummary(DailyOrderSummaryJobParam param, XxlJobRunContext context) {
        BusinessAssert.notNull(param.getBusinessDate(), "businessDate不能为空");
        String executionKey = "order-summary:" + param.getBusinessDate() + ":v" + param.getRunVersion();
        XxlLearningExecution execution = claimOrRead(
                executionKey, SUMMARY_HANDLER, param.getLeaseSeconds(), context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "该日期和版本的订单日报已经成功，本次幂等跳过";
        }
        try {
            XxlLearningOrderSummary summary = transactionService.writeDailySummary(param, execution);
            return "订单日报完成，日期=" + summary.getSummaryDate()
                    + "，版本=" + summary.getRunVersion() + "，订单数=" + summary.getOrderCount();
        } catch (RuntimeException exception) {
            failAfterBusinessRollback(execution, exception);
            throw exception;
        }
    }

    /**
     * 父任务生成工作批次。
     *
     * <ol>
     *     <li>幂等键包含 batchKey 和全部生成参数，所以参数漂移不会被旧 SUCCESS 静默吞掉；</li>
     *     <li>批次、generate_series 生成的全部工作项和 SUCCESS 在同一事务提交；</li>
     *     <li>相同 batchKey 配不同参数时，由批次唯一键读取旧数据并明确拒绝；</li>
     *     <li>只有父任务成功返回后，Admin 才会按 child_jobid 触发处理任务。</li>
     * </ol>
     */
    @Override
    public String generateWorkBatch(GenerateWorkBatchJobParam param, XxlJobRunContext context) {
        param.setBatchKey(param.getBatchKey().trim());
        String executionKey = "batch-generate:" + param.getBatchKey()
                + ":" + param.getItemCount() + ":" + param.getFailEvery() + ":" + param.getFailTimes();
        XxlLearningExecution execution = claimOrRead(executionKey, GENERATE_HANDLER, 120, context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "相同参数的工作批次已成功生成，本次幂等跳过";
        }
        try {
            XxlLearningBatch batch = transactionService.generateBatch(param, execution);
            return "工作批次生成完成，batchKey=" + batch.getBatchKey()
                    + "，itemCount=" + batch.getItemCount();
        } catch (RuntimeException exception) {
            failAfterBusinessRollback(execution, exception);
            throw exception;
        }
    }

    /**
     * 周期分片处理案例。
     *
     * <ol>
     *     <li>每次调度用 logId 建独立观察台账；真正的业务幂等由工作项租约和结果唯一键保证；</li>
     *     <li>短事务收口过期且耗尽次数的项目，并用 SKIP LOCKED 原子领取至多 batchSize 条；</li>
     *     <li>逐项在独立事务中计划失败或写唯一结果，某一项失败不回滚此前成功项；</li>
     *     <li>基于全部工作项刷新批次状态，而不是把“本轮领到0条”误判为完成；</li>
     *     <li>本轮正常完成后终结观察台账。1010 的固定频率负责下一轮持续驱动。</li>
     * </ol>
     */
    @Override
    public String processWorkItems(ProcessWorkItemsJobParam param, XxlJobRunContext context) {
        param.setBatchKey(param.getBatchKey().trim());
        validateShard(context);
        String processKeyPrefix = "work-process:" + param.getBatchKey()
                + ":" + context.getShardTotal() + ":" + context.getShardIndex() + ":log:";
        // 任一处理周期都可以顺手收口本 Handler 的全部过期观察台账；无需用用户 batchKey 做 LIKE 匹配。
        transactionService.closeExpiredProcessExecutions();
        XxlLearningBatch batch = transactionService.getBatch(param.getBatchKey());
        if (isTerminal(batch.getStatus())) {
            // 1010 是周期扫描任务。终态后不再为每个空轮次创建 execution 行，避免观察表无限增长。
            return "工作批次已经终结，无需继续领取，状态=" + batch.getStatus().getValue();
        }
        String executionKey = processKeyPrefix + context.getLogId();
        XxlLearningExecution execution = claimOrRead(
                executionKey, PROCESS_HANDLER, param.getLeaseSeconds(), context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "该分片调度日志已处理成功，本次幂等跳过";
        }

        int successCount = 0;
        int retryCount = 0;
        try {
            List<XxlLearningWorkItem> items = transactionService.claimWorkItems(param, context);
            for (XxlLearningWorkItem item : items) {
                // plannedFailures=2 表示第1、2次进入失败等待，第3次才产生唯一成功结果。
                if (item.getAttemptCount() <= item.getPlannedFailures()) {
                    transactionService.failWorkItem(item, param);
                    retryCount++;
                } else {
                    transactionService.succeedWorkItem(item, execution, context);
                    successCount++;
                }
            }
            XxlLearningBatch refreshed = transactionService.refreshBatch(batch.getId());
            String result = "本轮处理完成，领取=" + items.size() + "，成功=" + successCount
                    + "，待重试=" + retryCount + "，批次状态=" + refreshed.getStatus().getValue();
            transactionService.succeedExecution(execution, result);
            return result;
        } catch (RuntimeException exception) {
            failAfterBusinessRollback(execution, exception);
            throw exception;
        }
    }

    /** claim 返回空时重读数据库，不能把 SUCCESS、活跃 RUNNING 和不存在混成同一种结果。 */
    private XxlLearningExecution claimOrRead(
            String executionKey, String handlerName, int leaseSeconds, XxlJobRunContext context) {
        XxlLearningExecution claimed = transactionService.claimExecution(
                executionKey, handlerName, leaseSeconds, context);
        if (claimed != null) {
            return claimed;
        }
        XxlLearningExecution current = BusinessAssert.notNull(
                transactionService.getExecution(executionKey), "执行台账不存在");
        if (current.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return current;
        }
        throw new BusinessException("相同业务任务仍由其他执行器持有有效租约，请稍后重试");
    }

    /**
     * 业务短事务已经回滚后，再用独立事务尽力记录 FAILED。
     * 若租约已经被其他 Worker 接管，失败回写会影响0行；此时保留原异常并把回写异常挂为 suppressed。
     */
    private void failAfterBusinessRollback(XxlLearningExecution execution, RuntimeException original) {
        try {
            transactionService.failExecution(execution, original.getMessage());
        } catch (RuntimeException markFailure) {
            original.addSuppressed(markFailure);
        }
    }

    private void validateShard(XxlJobRunContext context) {
        BusinessAssert.isTrue(context.getShardTotal() > 0, "shardTotal必须大于0");
        BusinessAssert.isTrue(context.getShardIndex() >= 0
                        && context.getShardIndex() < context.getShardTotal(),
                "shardIndex必须位于[0, shardTotal)范围内");
    }

    private boolean isTerminal(XxlLearningBatchStatus status) {
        return status == XxlLearningBatchStatus.SUCCESS
                || status == XxlLearningBatchStatus.PARTIAL_SUCCESS
                || status == XxlLearningBatchStatus.FAILED;
    }
}
