package com.xt.xiaoxingxing.playground.xxljob.service;

import com.xt.xiaoxingxing.playground.xxljob.config.XxlJobNames;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkItem;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkResult;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningBatchMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningExecutionMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningOrderSummaryMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningWorkItemMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningWorkResultMapper;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobRunContext;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** XXL-JOB 业务的独立短事务服务。 */
@Service
@RequiredArgsConstructor
public class XxlJobTransactionService {

    private final XxlLearningExecutionMapper executionMapper;
    private final XxlLearningOrderSummaryMapper orderSummaryMapper;
    private final XxlLearningBatchMapper batchMapper;
    private final XxlLearningWorkItemMapper workItemMapper;
    private final XxlLearningWorkResultMapper workResultMapper;

    /** 原子领取执行租约；未取得时返回 null。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlLearningExecution claimExecution(
            String executionKey, String handlerName, int leaseSeconds, XxlJobRunContext context) {
        XxlLearningExecution candidate = new XxlLearningExecution();
        candidate.setExecutionKey(executionKey);
        candidate.setHandlerName(handlerName);
        candidate.setLeaseToken(UUID.randomUUID().toString());
        candidate.setJobId(context.getJobId());
        candidate.setLogId(context.getLogId());
        candidate.setLogDateTime(context.getLogDateTime());
        candidate.setShardIndex(context.getShardIndex());
        candidate.setShardTotal(context.getShardTotal());
        return executionMapper.claim(candidate, leaseSeconds);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW,
            readOnly = true)
    public XxlLearningExecution getExecution(String executionKey) {
        return executionMapper.selectByExecutionKey(executionKey);
    }

    /** 关闭过期的分片处理台账。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void closeExpiredProcessExecutions() {
        executionMapper.closeExpiredRunning(
                XxlJobNames.PROCESS_WORK_ITEMS,
                "Executor在租约内未完成本轮回写，后续周期将过期观察台账收口为FAILED");
    }

    /** 在独立事务中记录执行失败。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void failExecution(XxlLearningExecution execution, String error) {
        int affected = executionMapper.markFailed(
                execution.getId(), execution.getLeaseToken(), truncate(error));
        BusinessAssert.isTrue(affected == 1, "执行租约已失效，不能写入失败状态");
    }

    /** 在独立事务中记录执行成功。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void succeedExecution(XxlLearningExecution execution, String result) {
        int affected = executionMapper.markSuccess(
                execution.getId(), execution.getLeaseToken(), truncate(result));
        BusinessAssert.isTrue(affected == 1, "执行租约已失效，不能写入成功状态");
    }

    /** 续期当前执行权；租约已过期时拒绝恢复。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void renewExecutionLease(XxlLearningExecution execution, int leaseSeconds) {
        int affected = executionMapper.renewLease(
                execution.getId(), execution.getLeaseToken(), leaseSeconds);
        BusinessAssert.isTrue(affected == 1, "执行租约已过期或被其他执行器接管");
    }

    /** 在同一事务中生成日报并完成执行台账。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlLearningOrderSummary writeDailySummary(
            DailyOrderSummaryJobParam param, XxlLearningExecution execution) {
        LocalDateTime start = param.getBusinessDate().atStartOfDay();
        LocalDateTime end = param.getBusinessDate().plusDays(1).atStartOfDay();
        XxlLearningOrderSummary summary = orderSummaryMapper.aggregateOrders(start, end);
        summary.setSummaryDate(param.getBusinessDate());
        summary.setRunVersion(param.getRunVersion());
        summary.setSourceStartAt(start);
        summary.setSourceEndAt(end);
        summary.setExecutionId(execution.getId());

        int changed = orderSummaryMapper.upsertHigherVersion(summary);
        XxlLearningOrderSummary current = BusinessAssert.notNull(
                orderSummaryMapper.selectByDate(param.getBusinessDate()), "订单日报写入后未找到数据");
        BusinessAssert.isTrue(current.getRunVersion() <= param.getRunVersion(),
                "该业务日已存在更高版本日报，当前低版本不能覆盖");

        String result = changed == 1
                ? "订单日报已生成，日期=" + param.getBusinessDate() + "，版本=" + param.getRunVersion()
                : "相同版本订单日报已存在，本次幂等跳过";
        BusinessAssert.isTrue(executionMapper.markSuccess(
                execution.getId(), execution.getLeaseToken(), truncate(result)) == 1,
                "订单日报执行租约已失效，本次写入已回滚");
        return current;
    }

    /** 在同一事务中生成批次、工作项和成功状态。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlLearningBatch generateBatch(
            GenerateWorkBatchJobParam param, XxlLearningExecution execution) {
        XxlLearningBatch candidate = new XxlLearningBatch();
        candidate.setBatchKey(param.getBatchKey());
        candidate.setItemCount(param.getItemCount());
        candidate.setFailEvery(param.getFailEvery());
        candidate.setFailTimes(param.getFailTimes());
        candidate.setGeneratedExecutionId(execution.getId());
        batchMapper.insertIfAbsent(candidate);

        XxlLearningBatch batch = BusinessAssert.notNull(
                batchMapper.selectByBatchKey(param.getBatchKey()), "工作批次创建失败");
        // 同一 batchKey 不能混用不同生成参数。
        BusinessAssert.isTrue(batch.getItemCount() == param.getItemCount()
                        && batch.getFailEvery() == param.getFailEvery()
                        && batch.getFailTimes() == param.getFailTimes(),
                "相同batchKey已经绑定不同生成参数，不能混用两次实验");

        workItemMapper.insertGeneratedItems(
                batch.getId(), param.getItemCount(), param.getFailEvery(), param.getFailTimes());
        BusinessAssert.isTrue(workItemMapper.countByBatchId(batch.getId()) == param.getItemCount(),
                "工作项数量不完整，整笔批次生成已回滚");
        String result = "工作批次准备完成，batchKey=" + param.getBatchKey()
                + "，itemCount=" + param.getItemCount();
        BusinessAssert.isTrue(executionMapper.markSuccess(
                execution.getId(), execution.getLeaseToken(), truncate(result)) == 1,
                "批次生成执行租约已失效，整笔写入已回滚");
        return batch;
    }

    /** 收口过期工作项并领取当前分片的一批任务。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public WorkItemClaim claimWorkItems(
            ProcessWorkItemsJobParam param, XxlJobRunContext context) {
        Set<Long> affectedBatchIds = new HashSet<>();
        affectedBatchIds.addAll(workItemMapper.closeExpiredExhausted(
                param.getMaxAttempts(), context.getShardIndex(), context.getShardTotal(),
                "最后一次尝试期间租约过期，周期扫描将工作项收口为DEAD"));
        affectedBatchIds.addAll(workItemMapper.closeRetryWaitExhausted(
                param.getMaxAttempts(), context.getShardIndex(), context.getShardTotal(),
                "处理参数中的maxAttempts已不大于既有尝试次数，周期扫描将工作项收口为DEAD"));
        List<XxlLearningWorkItem> items = workItemMapper.claimShardItems(
                param.getBatchSize(), UUID.randomUUID().toString(),
                param.getLeaseSeconds(), context.getJobId(), context.getLogId(), param.getMaxAttempts(),
                context.getShardIndex(), context.getShardTotal());
        items.forEach(item -> affectedBatchIds.add(item.getBatchId()));
        return new WorkItemClaim(items, affectedBatchIds);
    }

    /** 在同一短事务中续期本轮执行权和即将处理的工作项。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void renewProcessingLeases(
            XxlLearningExecution execution, XxlLearningWorkItem item, int leaseSeconds) {
        BusinessAssert.isTrue(executionMapper.renewLease(
                        execution.getId(), execution.getLeaseToken(), leaseSeconds) == 1,
                "执行租约已过期或被其他执行器接管");
        BusinessAssert.isTrue(workItemMapper.renewLease(
                        item.getId(), item.getLeaseToken(), leaseSeconds) == 1,
                "工作项租约已过期或被其他执行器接管");
    }

    /** 在独立事务中记录单个工作项失败。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void failWorkItem(XxlLearningWorkItem item, ProcessWorkItemsJobParam param) {
        String error = "学习案例计划失败，第" + item.getAttemptCount() + "次尝试";
        int affected;
        if (item.getAttemptCount() >= param.getMaxAttempts()) {
            affected = workItemMapper.markDead(item.getId(), item.getLeaseToken(), error);
        } else {
            affected = workItemMapper.markRetryWait(
                    item.getId(), item.getLeaseToken(), param.getRetryDelaySeconds(), error);
        }
        BusinessAssert.isTrue(affected == 1, "工作项租约已失效，失败推进未生效");
    }

    /** 在同一事务中写入唯一结果并完成工作项。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void succeedWorkItem(
            XxlLearningWorkItem item, XxlLearningExecution execution, XxlJobRunContext context) {
        XxlLearningWorkResult result = new XxlLearningWorkResult();
        result.setWorkItemId(item.getId());
        result.setBatchId(item.getBatchId());
        result.setItemNo(item.getItemNo());
        result.setExecutionId(execution.getId());
        result.setResultValue("item-" + item.getItemNo() + "-processed");
        result.setJobId(context.getJobId());
        result.setLogId(context.getLogId());
        result.setShardIndex(context.getShardIndex());
        result.setShardTotal(context.getShardTotal());
        workResultMapper.insertIfAbsent(result);
        BusinessAssert.isTrue(workItemMapper.markSuccess(item.getId(), item.getLeaseToken()) == 1,
                "工作项租约已失效，结果写入和状态更新已一起回滚");
    }

    /** 根据全部工作项刷新批次状态。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlLearningBatch refreshBatch(long batchId) {
        // 锁定批次，防止并发分片用旧快照回退状态。
        BusinessAssert.notNull(batchMapper.selectByIdForUpdate(batchId), "工作批次不存在");
        return BusinessAssert.notNull(batchMapper.refreshStatusReturning(batchId), "工作批次不存在");
    }

    /** 查询仍需周期驱动的批次。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW,
            readOnly = true)
    public List<Long> listActiveBatchIds() {
        return batchMapper.selectActiveIds();
    }

    private String truncate(String value) {
        String normalized = value == null || value.isBlank() ? "未提供原因" : value;
        return normalized.substring(0, Math.min(normalized.length(), 1000));
    }

    /** 一轮领取的工作项及需要刷新状态的批次。 */
    public record WorkItemClaim(List<XxlLearningWorkItem> items, Set<Long> affectedBatchIds) {
    }
}
