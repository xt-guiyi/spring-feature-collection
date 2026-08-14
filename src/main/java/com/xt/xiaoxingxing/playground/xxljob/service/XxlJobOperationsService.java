package com.xt.xiaoxingxing.playground.xxljob.service;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkItem;
import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkResult;
import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningBatchStatus;
import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningExecutionStatus;
import com.xt.xiaoxingxing.playground.xxljob.enums.XxlLearningWorkItemStatus;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningBatchMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningExecutionMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningOrderSummaryMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningWorkItemMapper;
import com.xt.xiaoxingxing.playground.xxljob.mapper.XxlLearningWorkResultMapper;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningBatchVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningExecutionVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningOrderSummaryVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningWorkItemVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningWorkResultVO;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * XXL-JOB 学习模块的只读业务观察服务。
 *
 * <p>XXL-JOB Admin 负责调度配置、触发和调度日志，本服务读取的是 PostgreSQL 中真正提交的业务结果。
 * 两边一起观察，才能区分“调度请求送达”和“业务事务已经完成”。</p>
 *
 * <p>类上的只读事务明确选择 {@code playgroundTransactionManager}。项目存在多个数据源时，省略事务管理器
 * 可能读到错误数据库；{@code readOnly = true} 也向维护者说明这里不应该出现状态推进 SQL。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
public class XxlJobOperationsService {

    private static final Set<String> EXECUTION_STATUSES = enumValues(XxlLearningExecutionStatus.values());
    private static final Set<String> BATCH_STATUSES = enumValues(XxlLearningBatchStatus.values());
    private static final Set<String> WORK_ITEM_STATUSES = enumValues(XxlLearningWorkItemStatus.values());

    private final XxlLearningExecutionMapper executionMapper;
    private final XxlLearningOrderSummaryMapper orderSummaryMapper;
    private final XxlLearningBatchMapper batchMapper;
    private final XxlLearningWorkItemMapper workItemMapper;
    private final XxlLearningWorkResultMapper workResultMapper;

    /** 按 Handler 和执行终态分页观察幂等执行链。两个过滤条件都可以不传。 */
    public PageResult<XxlLearningExecutionVO> pageExecutions(
            String handlerName, String status, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        String normalizedHandlerName = normalizeOptional(handlerName);
        if (normalizedHandlerName != null) {
            BusinessAssert.isTrue(normalizedHandlerName.length() <= 100, "handlerName长度不能超过100");
        }
        String normalizedStatus = validateOptionalStatus(status, EXECUTION_STATUSES, "执行台账");
        long offset = offset(pageNum, pageSize);
        List<XxlLearningExecution> rows = executionMapper.selectPage(
                normalizedHandlerName, normalizedStatus, offset, pageSize);
        long total = executionMapper.countPage(normalizedHandlerName, normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, XxlLearningExecutionVO::from);
    }

    /** 查询一条完整执行台账；不存在时转换成统一业务异常，不把 null 当成成功数据返回。 */
    public XxlLearningExecutionVO getExecution(long id) {
        BusinessAssert.isTrue(id > 0, "执行台账id必须大于0");
        return XxlLearningExecutionVO.from(BusinessAssert.notNull(
                executionMapper.selectById(id), "执行台账不存在"));
    }

    /** 按闭区间 [dateFrom, dateTo] 分页查看业务日报；任一端不传表示该方向不设边界。 */
    public PageResult<XxlLearningOrderSummaryVO> pageOrderSummaries(
            LocalDate dateFrom, LocalDate dateTo, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        if (dateFrom != null && dateTo != null) {
            BusinessAssert.isTrue(!dateFrom.isAfter(dateTo), "dateFrom不能晚于dateTo");
        }
        long offset = offset(pageNum, pageSize);
        List<XxlLearningOrderSummary> rows = orderSummaryMapper.selectPage(dateFrom, dateTo, offset, pageSize);
        long total = orderSummaryMapper.countPage(dateFrom, dateTo);
        return toPage(rows, total, pageNum, pageSize, XxlLearningOrderSummaryVO::from);
    }

    /** 按批次状态分页查询；批次状态由数据库基于全部工作项计算。 */
    public PageResult<XxlLearningBatchVO> pageBatches(String status, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        String normalizedStatus = validateOptionalStatus(status, BATCH_STATUSES, "批次");
        long offset = offset(pageNum, pageSize);
        List<XxlLearningBatch> rows = batchMapper.selectPage(normalizedStatus, offset, pageSize);
        long total = batchMapper.countPage(normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, XxlLearningBatchVO::from);
    }

    /**
     * 查看指定业务批次中的工作项。
     *
     * <p>这里按 {@code batchKey} 而不是内部自增 id 查询，更方便把接口结果与任务参数、幂等键对应起来。</p>
     */
    public PageResult<XxlLearningWorkItemVO> pageWorkItems(
            String batchKey, String status, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        String normalizedBatchKey = requireBatchKey(batchKey);
        String normalizedStatus = validateOptionalStatus(status, WORK_ITEM_STATUSES, "工作项");
        long offset = offset(pageNum, pageSize);
        List<XxlLearningWorkItem> rows = workItemMapper.selectPageByBatchKey(
                normalizedBatchKey, normalizedStatus, offset, pageSize);
        long total = workItemMapper.countPageByBatchKey(normalizedBatchKey, normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, XxlLearningWorkItemVO::from);
    }

    /** 查看指定批次已经产生的唯一成功结果，用于验证失败重试没有制造重复副作用。 */
    public PageResult<XxlLearningWorkResultVO> pageWorkResults(
            String batchKey, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        String normalizedBatchKey = requireBatchKey(batchKey);
        long offset = offset(pageNum, pageSize);
        List<XxlLearningWorkResult> rows = workResultMapper.selectPage(normalizedBatchKey, offset, pageSize);
        long total = workResultMapper.countPage(normalizedBatchKey);
        return toPage(rows, total, pageNum, pageSize, XxlLearningWorkResultVO::from);
    }

    /** 分页规则在 Service 再校验一次，保证定时任务或其他 Java 调用者也不能绕过 Controller 校验。 */
    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum > 0, "pageNum必须大于0");
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 100, "pageSize必须在1到100之间");
    }

    private long offset(int pageNum, int pageSize) {
        return (long) (pageNum - 1) * pageSize;
    }

    private String requireBatchKey(String batchKey) {
        BusinessAssert.hasText(batchKey, "batchKey不能为空");
        String normalized = batchKey.trim();
        BusinessAssert.isTrue(normalized.length() <= 200, "batchKey长度不能超过200");
        return normalized;
    }

    /** 空白可选条件等价于“不筛选”；非空状态必须是数据库约束允许的正式状态。 */
    private String validateOptionalStatus(String status, Set<String> allowedStatuses, String fieldName) {
        String normalized = normalizeOptional(status);
        if (normalized == null) {
            return null;
        }
        BusinessAssert.isTrue(allowedStatuses.contains(normalized),
                fieldName + "status不合法，可选值：" + allowedStatuses);
        return normalized;
    }

    private String normalizeOptional(String value) {
        return BusinessAssert.hasText(value) ? value.trim() : null;
    }

    /** 统一组装分页元数据，避免五个接口各写一份容易漏字段的复制代码。 */
    private <S, T> PageResult<T> toPage(
            List<S> rows, long total, int pageNum, int pageSize, Function<S, T> converter) {
        PageResult<T> result = new PageResult<>();
        result.setList(rows.stream().map(converter).toList());
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    private static Set<String> enumValues(XxlLearningExecutionStatus[] statuses) {
        return Arrays.stream(statuses)
                .map(XxlLearningExecutionStatus::getValue)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> enumValues(XxlLearningBatchStatus[] statuses) {
        return Arrays.stream(statuses)
                .map(XxlLearningBatchStatus::getValue)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> enumValues(XxlLearningWorkItemStatus[] statuses) {
        return Arrays.stream(statuses)
                .map(XxlLearningWorkItemStatus::getValue)
                .collect(Collectors.toUnmodifiableSet());
    }
}
