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
        // 1. 根据业务键领取执行权。
        param.setBusinessKey(param.getBusinessKey().trim());
        String executionKey = "retry:" + param.getBusinessKey();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.RETRY, param.getLeaseSeconds(), context);

        // 2. 已成功的业务直接返回，避免重复执行。
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "业务键已经成功，本次幂等跳过，attemptCount=" + execution.getAttemptCount();
        }

        // 3. 前 failTimes 次记录失败，由 Admin 继续重试。
        if (execution.getAttemptCount() <= param.getFailTimes()) {
            String error = "学习案例按计划失败：attempt=" + execution.getAttemptCount()
                    + "，failTimes=" + param.getFailTimes();
            transactionService.failExecution(execution, error);
            throw new IllegalStateException(error);
        }

        // 4. 达到成功次数后完成本次业务。
        String result = "重试任务成功，businessKey=" + param.getBusinessKey()
                + "，attemptCount=" + execution.getAttemptCount();
        transactionService.succeedExecution(execution, result);
        return result;
    }

    /** 按业务日期和版本幂等生成日报。 */
    public String runDailyOrderSummary(DailyOrderSummaryJobParam param, XxlJobRunContext context) {
        // 1. 按业务日期和版本领取执行权。
        BusinessAssert.notNull(param.getBusinessDate(), "businessDate不能为空");
        String executionKey = "order-summary:" + param.getBusinessDate() + ":v" + param.getRunVersion();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.DAILY_ORDER_SUMMARY, param.getLeaseSeconds(), context);

        // 2. 相同日期和版本已经完成时直接返回。
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "该日期和版本的订单日报已经成功，本次幂等跳过";
        }

        // 3. 汇总订单并保存日报。
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
        // 1. 根据批次参数领取执行权。
        param.setBatchKey(param.getBatchKey().trim());
        String executionKey = "batch-generate:" + param.getBatchKey()
                + ":" + param.getItemCount() + ":" + param.getFailEvery() + ":" + param.getFailTimes();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.GENERATE_WORK_BATCH, 120, context);

        // 2. 相同批次已经生成时直接返回。
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "相同参数的工作批次已成功生成，本次幂等跳过";
        }

        // 3. 创建批次及其全部工作项。
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
        // 1. 第 0 号分片清理上次未正常结束的执行记录。
        if (context.shardIndex() == 0) {
            transactionService.closeExpiredProcessExecutions();
        }

        // 2. 领取当前分片的本轮执行权。
        String executionKey = "work-process:" + context.shardTotal() + ":"
                + context.shardIndex() + ":log:" + context.logId();
        XxlLearningExecution execution = claimOrRead(
                executionKey, XxlJobNames.PROCESS_WORK_ITEMS, param.getLeaseSeconds(), context);
        if (execution.getStatus() == XxlLearningExecutionStatus.SUCCESS) {
            return "该分片调度日志已处理成功，本次幂等跳过";
        }

        int successCount = 0;
        int retryCount = 0;
        try {
            // 3. 领取当前分片的一批工作项。
            XxlJobTransactionService.WorkItemClaim claim = transactionService.claimWorkItems(param, context);
            List<XxlLearningWorkItem> items = claim.items();

            // 4. 逐条处理，成功项完成，计划失败项等待重试。
            for (XxlLearningWorkItem item : items) {
                checkInterrupted();
                transactionService.renewProcessingLeases(execution, item, param.getLeaseSeconds());
                if (item.getAttemptCount() <= item.getPlannedFailures()) {
                    transactionService.failWorkItem(item, param);
                    retryCount++;
                } else {
                    transactionService.succeedWorkItem(item, execution, context);
                    successCount++;
                }
            }

            // 5. 根据全部工作项重新计算批次状态。
            Set<Long> batchIdsToRefresh = new TreeSet<>(claim.affectedBatchIds());
            if (context.shardIndex() == 0) {
                batchIdsToRefresh.addAll(transactionService.listActiveBatchIds());
            }
            for (Long batchId : batchIdsToRefresh) {
                checkInterrupted();
                transactionService.renewExecutionLease(execution, param.getLeaseSeconds());
                transactionService.refreshBatch(batchId);
            }

            // 6. 保存本轮处理结果。
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

        // 已成功则幂等返回，仍在执行则拒绝并发处理。
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
