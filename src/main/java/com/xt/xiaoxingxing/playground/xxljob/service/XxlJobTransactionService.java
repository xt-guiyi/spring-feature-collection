package com.xt.xiaoxingxing.playground.xxljob.service;

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
import java.util.List;
import java.util.UUID;

/**
 * XXL-JOB 业务的短事务边界。
 *
 * <p>这个类与编排用的 {@link XxlJobLearningService} 分成两个 Spring Bean，是有意为之：
 * Spring 事务通过代理生效，同一个类里直接调用自己的 {@code REQUIRES_NEW} 方法不会经过代理。
 * 调度重试案例如果把“领取、业务、失败标记”包进一个最终会抛异常的大事务，失败标记也会一起回滚，
 * 下一次重试将永远以为自己还是第一次执行。</p>
 */
@Service
@RequiredArgsConstructor
public class XxlJobTransactionService {

    private final XxlLearningExecutionMapper executionMapper;
    private final XxlLearningOrderSummaryMapper orderSummaryMapper;
    private final XxlLearningBatchMapper batchMapper;
    private final XxlLearningWorkItemMapper workItemMapper;
    private final XxlLearningWorkResultMapper workResultMapper;

    /**
     * 原子取得一条稳定业务执行链的执行权。
     *
     * <p>首次插入、FAILED 重试和过期 RUNNING 接管都由一条 PostgreSQL
     * {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING} 完成。返回 null 表示本次没有取得执行权，
     * 调用方必须重读状态，区分已经成功和仍被其他 Worker 持有。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlLearningExecution claimExecution(
            String executionKey, String handlerName, int leaseSeconds, XxlJobRunContext context) {
        BusinessAssert.hasText(executionKey, "executionKey不能为空");
        BusinessAssert.isTrue(executionKey.length() <= 300, "executionKey长度不能超过300");
        BusinessAssert.hasText(handlerName, "handlerName不能为空");
        BusinessAssert.isTrue(handlerName.length() <= 100, "handlerName长度不能超过100");
        BusinessAssert.isTrue(leaseSeconds >= 5 && leaseSeconds <= 3600,
                "leaseSeconds必须在5到3600之间");
        validateShard(context);
        XxlLearningExecution candidate = new XxlLearningExecution();
        candidate.setExecutionKey(executionKey);
        candidate.setHandlerName(handlerName);
        candidate.setLeaseToken(UUID.randomUUID().toString());
        candidate.setJobId(context.getJobId());
        candidate.setLogId(context.getLogId());
        candidate.setLogDateTime(context.getLogDateTime());
        candidate.setLogFileName(context.getLogFileName());
        candidate.setShardIndex(context.getShardIndex());
        candidate.setShardTotal(context.getShardTotal());
        return executionMapper.claim(candidate, leaseSeconds);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW,
            readOnly = true)
    public XxlLearningExecution getExecution(String executionKey) {
        return executionMapper.selectByExecutionKey(executionKey);
    }

    /** 收口本 Handler 全部过期观察台账，避免进程硬崩溃留下永久 RUNNING。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public int closeExpiredProcessExecutions() {
        return executionMapper.closeExpiredRunning(
                "xxlProcessWorkItemsJobHandler",
                "Executor在租约内未完成本轮回写，后续周期将过期观察台账收口为FAILED");
    }

    /** 故意失败必须先在独立事务留下 FAILED 事实，外层随后抛异常也不能把它回滚。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void failExecution(XxlLearningExecution execution, String error) {
        int affected = executionMapper.markFailed(
                execution.getId(), execution.getLeaseToken(), truncate(error));
        BusinessAssert.isTrue(affected == 1, "执行租约已失效，不能写入失败状态");
    }

    /** 没有额外业务副作用的重试演示，可以直接在这个短事务内提交 SUCCESS。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void succeedExecution(XxlLearningExecution execution, String result) {
        int affected = executionMapper.markSuccess(
                execution.getId(), execution.getLeaseToken(), truncate(result));
        BusinessAssert.isTrue(affected == 1, "执行租约已失效，不能写入成功状态");
    }

    /**
     * 聚合、版本写入和租约终结必须在同一个事务中提交。
     * 若聚合期间租约过期并已被其他 Worker 接管，最后的条件 UPDATE 会影响 0 行并让整笔日报写入回滚。
     */
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
        XxlLearningOrderSummary current = orderSummaryMapper.selectByDate(param.getBusinessDate());
        BusinessAssert.notNull(current, "订单日报写入后未找到数据");
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

    /**
     * 批次、全部工作项和执行 SUCCESS 在一个事务内生成，避免只创建半个批次。
     * 同 batchKey 同参数是幂等重放；同 key 参数不同则明确拒绝。
     */
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

        XxlLearningBatch batch = batchMapper.selectByBatchKey(param.getBatchKey());
        BusinessAssert.notNull(batch, "工作批次创建失败");
        // 显式判空并拆箱后比较业务值，避免数据库脏数据在这里表现成难懂的自动拆箱 NPE。
        BusinessAssert.isTrue(batch.getItemCount() != null
                        && batch.getItemCount().intValue() == param.getItemCount()
                        && batch.getFailEvery() != null
                        && batch.getFailEvery().intValue() == param.getFailEvery()
                        && batch.getFailTimes() != null
                        && batch.getFailTimes().intValue() == param.getFailTimes(),
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

    /** 每轮先把“最后一次处理中崩溃”的过期项目收口，再原子领取当前分片的一小批工作。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public List<XxlLearningWorkItem> claimWorkItems(
            ProcessWorkItemsJobParam param, XxlJobRunContext context) {
        validateShard(context);
        workItemMapper.closeExpiredExhausted(
                param.getBatchKey(), param.getMaxAttempts(), context.getShardIndex(), context.getShardTotal(),
                "最后一次尝试期间租约过期，周期扫描将工作项收口为DEAD");
        workItemMapper.closeRetryWaitExhausted(
                param.getBatchKey(), param.getMaxAttempts(), context.getShardIndex(), context.getShardTotal(),
                "处理参数中的maxAttempts已不大于既有尝试次数，周期扫描将工作项收口为DEAD");
        return workItemMapper.claimShardItems(
                param.getBatchKey(), param.getBatchSize(), UUID.randomUUID().toString(),
                param.getLeaseSeconds(), context.getJobId(), context.getLogId(), param.getMaxAttempts(),
                context.getShardIndex(), context.getShardTotal());
    }

    /** 计划失败只推进当前工作项；单项事务失败不应回滚同一轮其他工作项的结果。 */
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

    /**
     * 唯一结果与工作项 SUCCESS 同事务提交。
     * 结果 INSERT 因重复影响 0 行时仍继续条件终结，支持“结果曾成功但工作项终态未知”的安全恢复。
     */
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

    /** 刷新使用全表状态计数；本轮领取 0 行不代表批次已经完成。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public XxlLearningBatch refreshBatch(long batchId) {
        // 先锁批次行。后来的分片必须等前一事务提交后再执行后面的 counts 查询，不能拿旧快照回退状态。
        BusinessAssert.notNull(batchMapper.selectByIdForUpdate(batchId), "工作批次不存在");
        return BusinessAssert.notNull(batchMapper.refreshStatusReturning(batchId), "工作批次不存在");
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW,
            readOnly = true)
    public XxlLearningBatch getBatch(String batchKey) {
        return BusinessAssert.notNull(batchMapper.selectByBatchKey(batchKey), "工作批次不存在");
    }

    private void validateShard(XxlJobRunContext context) {
        BusinessAssert.isTrue(context.getShardTotal() > 0, "shardTotal必须大于0");
        BusinessAssert.isTrue(context.getShardIndex() >= 0
                        && context.getShardIndex() < context.getShardTotal(),
                "shardIndex必须位于[0, shardTotal)范围内");
    }

    private String truncate(String value) {
        String normalized = value == null || value.isBlank() ? "未提供原因" : value;
        return normalized.substring(0, Math.min(normalized.length(), 1000));
    }
}
