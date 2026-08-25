package com.xt.xiaoxingxing.playground.xxljob.service;

import com.xt.xiaoxingxing.playground.xxljob.config.XxlJobNames;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.RetryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkItem;
import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningExecutionStatus;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobRunContext;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 编排 XXL-JOB 学习业务，事务由短事务服务分段提交。 */
@Service
@RequiredArgsConstructor
public class XxlJobLearningService {

    private final XxlJobTransactionService transactionService;

    /** 演示失败重试与租约幂等。 */
    public String runRetry(RetryJobParam param, XxlJobRunContext context) {
        param.setBusinessKey(param.getBusinessKey().trim());
        String executionKey = "retry:" + param.getBusinessKey();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.RETRY, param.getLeaseSeconds(), context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "业务键已经成功，本次幂等跳过，attemptCount=" + execution.getAttemptCount();
        }

        if (execution.getAttemptCount() <= param.getFailTimes()) {
            String error = "学习案例按计划失败：attempt=" + execution.getAttemptCount()
                    + "，failTimes=" + param.getFailTimes();
            transactionService.failExecution(execution, error);
            // 先记录失败，再抛给 Admin 触发重试。
            throw new IllegalStateException(error);
        }

        String result = "重试任务成功，businessKey=" + param.getBusinessKey()
                + "，attemptCount=" + execution.getAttemptCount();
        transactionService.succeedExecution(execution, result);
        return result;
    }

    /** 按业务日期和版本幂等生成日报。 */
    public String runDailyOrderSummary(DailyOrderSummaryJobParam param, XxlJobRunContext context) {
        BusinessAssert.notNull(param.getBusinessDate(), "businessDate不能为空");
        String executionKey = "order-summary:" + param.getBusinessDate() + ":v" + param.getRunVersion();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.DAILY_ORDER_SUMMARY, param.getLeaseSeconds(), context);
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

    /** 幂等生成分片工作批次。 */
    public String generateWorkBatch(GenerateWorkBatchJobParam param, XxlJobRunContext context) {
        param.setBatchKey(param.getBatchKey().trim());
        String executionKey = "batch-generate:" + param.getBatchKey()
                + ":" + param.getItemCount() + ":" + param.getFailEvery() + ":" + param.getFailTimes();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.GENERATE_WORK_BATCH, 120, context);
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

    /** 按分片批量处理工作项。 */
    public String processWorkItems(ProcessWorkItemsJobParam param, XxlJobRunContext context) {
        if (context.getShardIndex() == 0) {
            transactionService.closeExpiredProcessExecutions();
        }
        String executionKey = "work-process:" + context.getShardTotal() + ":"
                + context.getShardIndex() + ":log:" + context.getLogId();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.PROCESS_WORK_ITEMS, param.getLeaseSeconds(), context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "该分片调度日志已处理成功，本次幂等跳过";
        }

        int successCount = 0;
        int retryCount = 0;
        try {
            XxlJobTransactionService.WorkItemClaim claim = transactionService.claimWorkItems(param, context);
            List<XxlLearningWorkItem> items = claim.items();
            for (XxlLearningWorkItem item : items) {
                checkInterrupted();
                transactionService.renewProcessingLeases(execution, item, param.getLeaseSeconds());
                // 未超过计划失败次数时进入重试等待。
                if (item.getAttemptCount() <= item.getPlannedFailures()) {
                    transactionService.failWorkItem(item, param);
                    retryCount++;
                } else {
                    transactionService.succeedWorkItem(item, execution, context);
                    successCount++;
                }
            }
            Set<Long> batchIdsToRefresh = new TreeSet<>(claim.affectedBatchIds());
            if (context.getShardIndex() == 0) {
                batchIdsToRefresh.addAll(transactionService.listActiveBatchIds());
            }
            for (Long batchId : batchIdsToRefresh) {
                checkInterrupted();
                transactionService.renewExecutionLease(execution, param.getLeaseSeconds());
                transactionService.refreshBatch(batchId);
            }
            checkInterrupted();
            transactionService.renewExecutionLease(execution, param.getLeaseSeconds());
            String result = "本轮处理完成，领取=" + items.size() + "，成功=" + successCount
                    + "，待重试=" + retryCount + "，刷新批次=" + batchIdsToRefresh.size();
            transactionService.succeedExecution(execution, result);
            return result;
        } catch (RuntimeException exception) {
            failAfterBusinessRollback(execution, exception);
            throw exception;
        }
    }

    /** 未取得租约时重读执行状态。 */
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

    /** 业务回滚后用独立事务记录失败。 */
    private void failAfterBusinessRollback(XxlLearningExecution execution, RuntimeException original) {
        try {
            transactionService.failExecution(execution, original.getMessage());
        } catch (RuntimeException markFailure) {
            original.addSuppressed(markFailure);
        }
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("任务线程已被中断，停止处理剩余工作项");
        }
    }
}
