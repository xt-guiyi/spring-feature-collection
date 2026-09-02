package com.xt.xiaoxingxing.playground.features.xxljob.service;

import com.xt.xiaoxingxing.playground.features.xxljob.constants.XxlJobNames;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.DailyOrderSummaryJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.GenerateWorkBatchJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.request.ProcessWorkItemsJobParam;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlBatch;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlExecution;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlOrderSummary;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlWorkItem;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlWorkResult;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlBatchMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlExecutionMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlOrderSummaryMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlWorkItemMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlWorkResultMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.support.XxlJobRunContext;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
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

    private final XxlExecutionMapper executionMapper;
    private final XxlOrderSummaryMapper orderSummaryMapper;
    private final XxlBatchMapper batchMapper;
    private final XxlWorkItemMapper workItemMapper;
    private final XxlWorkResultMapper workResultMapper;

    /** 原子领取执行租约；未取得时返回 null。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlExecution claimExecution(
            String executionKey, String handlerName, int leaseSeconds, XxlJobRunContext context) {
        XxlExecution candidate = new XxlExecution();
        candidate.setExecutionKey(executionKey);
        candidate.setHandlerName(handlerName);
        candidate.setLeaseToken(UUID.randomUUID().toString());
        candidate.setJobId(context.jobId());
        candidate.setLogId(context.logId());
        candidate.setLogDateTime(context.logDateTime());
        candidate.setShardIndex(context.shardIndex());
        candidate.setShardTotal(context.shardTotal());
        return executionMapper.claim(candidate, leaseSeconds);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW,
            readOnly = true)
    public XxlExecution getExecution(String executionKey) {
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
    public void failExecution(XxlExecution execution, String error) {
        int affected = executionMapper.markFailed(
                execution.getId(), execution.getLeaseToken(), truncate(error));
        BusinessAssert.isTrue(affected == 1, "执行租约已失效，不能写入失败状态");
    }

    /** 在独立事务中记录执行成功。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void succeedExecution(XxlExecution execution, String result) {
        int affected = executionMapper.markSuccess(
                execution.getId(), execution.getLeaseToken(), truncate(result));
        BusinessAssert.isTrue(affected == 1, "执行租约已失效，不能写入成功状态");
    }

    /** 续期当前执行权；租约已过期时拒绝恢复。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void renewExecutionLease(XxlExecution execution, int leaseSeconds) {
        int affected = executionMapper.renewLease(
                execution.getId(), execution.getLeaseToken(), leaseSeconds);
        BusinessAssert.isTrue(affected == 1, "执行租约已过期或被其他执行器接管");
    }

    /** 在同一事务中生成日报并完成执行台账。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlOrderSummary writeDailySummary(
            DailyOrderSummaryJobParam param, XxlExecution execution) {
        // 1. 汇总指定业务日的订单数据。
        LocalDateTime start = param.getBusinessDate().atStartOfDay();
        LocalDateTime end = param.getBusinessDate().plusDays(1).atStartOfDay();
        XxlOrderSummary summary = orderSummaryMapper.aggregateOrders(start, end);
        summary.setSummaryDate(param.getBusinessDate());
        summary.setRunVersion(param.getRunVersion());
        summary.setSourceStartAt(start);
        summary.setSourceEndAt(end);
        summary.setExecutionId(execution.getId());

        // 2. 保存日报并保留较高版本。
        int changed = orderSummaryMapper.upsertHigherVersion(summary);
        XxlOrderSummary current = BusinessAssert.notNull(
                orderSummaryMapper.selectByDate(param.getBusinessDate()), "订单日报写入后未找到数据");
        BusinessAssert.isTrue(current.getRunVersion() <= param.getRunVersion(),
                "该业务日已存在更高版本日报，当前低版本不能覆盖");

        // 3. 日报和执行成功状态在同一事务中提交。
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
    public XxlBatch generateBatch(
            GenerateWorkBatchJobParam param, XxlExecution execution) {
        // 1. 创建批次主记录。
        XxlBatch candidate = new XxlBatch();
        candidate.setBatchKey(param.getBatchKey());
        candidate.setItemCount(param.getItemCount());
        candidate.setFailEvery(param.getFailEvery());
        candidate.setFailTimes(param.getFailTimes());
        candidate.setGeneratedExecutionId(execution.getId());
        batchMapper.insertIfAbsent(candidate);

        // 2. 读取批次并确认生成参数一致。
        XxlBatch batch = BusinessAssert.notNull(
                batchMapper.selectByBatchKey(param.getBatchKey()), "工作批次创建失败");
        BusinessAssert.isTrue(batch.getItemCount() == param.getItemCount()
                        && batch.getFailEvery() == param.getFailEvery()
                        && batch.getFailTimes() == param.getFailTimes(),
                "相同batchKey已经绑定不同生成参数，不能混用两次实验");

        // 3. 一次生成批次下的全部工作项。
        workItemMapper.insertGeneratedItems(
                batch.getId(), param.getItemCount(), param.getFailEvery(), param.getFailTimes());
        BusinessAssert.isTrue(workItemMapper.countByBatchId(batch.getId()) == param.getItemCount(),
                "工作项数量不完整，整笔批次生成已回滚");

        // 4. 批次数据和执行成功状态在同一事务中提交。
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
        // 1. 将达到最大次数的过期工作项结束为 DEAD。
        Set<Long> affectedBatchIds = new HashSet<>();
        affectedBatchIds.addAll(workItemMapper.closeExpiredExhausted(
                param.getMaxAttempts(), context.shardIndex(), context.shardTotal(),
                "最后一次尝试期间租约过期，周期扫描将工作项收口为DEAD"));
        affectedBatchIds.addAll(workItemMapper.closeRetryWaitExhausted(
                param.getMaxAttempts(), context.shardIndex(), context.shardTotal(),
                "处理参数中的maxAttempts已不大于既有尝试次数，周期扫描将工作项收口为DEAD"));

        // 2. 领取当前分片本轮可以处理的工作项。
        List<XxlWorkItem> items = workItemMapper.claimShardItems(
                param.getBatchSize(), UUID.randomUUID().toString(),
                param.getLeaseSeconds(), context.jobId(), context.logId(), param.getMaxAttempts(),
                context.shardIndex(), context.shardTotal());
        items.forEach(item -> affectedBatchIds.add(item.getBatchId()));
        return new WorkItemClaim(items, affectedBatchIds);
    }

    /** 在同一短事务中续期本轮执行权和即将处理的工作项。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void renewProcessingLeases(
            XxlExecution execution, XxlWorkItem item, int leaseSeconds) {
        BusinessAssert.isTrue(executionMapper.renewLease(
                        execution.getId(), execution.getLeaseToken(), leaseSeconds) == 1,
                "执行租约已过期或被其他执行器接管");
        BusinessAssert.isTrue(workItemMapper.renewLease(
                        item.getId(), item.getLeaseToken(), leaseSeconds) == 1,
                "工作项租约已过期或被其他执行器接管");
    }

    /** 在独立事务中记录单个工作项失败。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void failWorkItem(XxlWorkItem item, ProcessWorkItemsJobParam param) {
        String error = "学习案例计划失败，第" + item.getAttemptCount() + "次尝试";
        int affected;

        // 达到最大次数后结束，否则等待下一轮重试。
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
            XxlWorkItem item, XxlExecution execution, XxlJobRunContext context) {
        // 1. 保存工作项的唯一处理结果。
        XxlWorkResult result = new XxlWorkResult();
        result.setWorkItemId(item.getId());
        result.setBatchId(item.getBatchId());
        result.setItemNo(item.getItemNo());
        result.setExecutionId(execution.getId());
        result.setResultValue("item-" + item.getItemNo() + "-processed");
        result.setJobId(context.jobId());
        result.setLogId(context.logId());
        result.setShardIndex(context.shardIndex());
        result.setShardTotal(context.shardTotal());
        workResultMapper.insertIfAbsent(result);

        // 2. 将工作项更新为成功。
        BusinessAssert.isTrue(workItemMapper.markSuccess(item.getId(), item.getLeaseToken()) == 1,
                "工作项租约已失效，结果写入和状态更新已一起回滚");
    }

    /** 根据全部工作项刷新批次状态。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlBatch refreshBatch(long batchId) {
        // 锁定批次后，根据全部工作项重新计算批次状态。
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
    public record WorkItemClaim(List<XxlWorkItem> items, Set<Long> affectedBatchIds) {
    }
}
