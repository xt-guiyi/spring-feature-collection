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

/** 查询 XXL-JOB 学习任务的业务结果。 */
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

    /** 分页查询执行台账。 */
    public PageResult<XxlLearningExecutionVO> pageExecutions(
            String handlerName, String status, int pageNum, int pageSize) {
        String normalizedHandlerName = normalizeOptional(handlerName);
        String normalizedStatus = validateOptionalStatus(status, EXECUTION_STATUSES, "执行台账");
        long offset = offset(pageNum, pageSize);
        List<XxlLearningExecution> rows = executionMapper.selectPage(
                normalizedHandlerName, normalizedStatus, offset, pageSize);
        long total = executionMapper.countPage(normalizedHandlerName, normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, XxlLearningExecutionVO::from);
    }

    /** 查询执行台账详情。 */
    public XxlLearningExecutionVO getExecution(long id) {
        return XxlLearningExecutionVO.from(BusinessAssert.notNull(
                executionMapper.selectById(id), "执行台账不存在"));
    }

    /** 分页查询订单日报。 */
    public PageResult<XxlLearningOrderSummaryVO> pageOrderSummaries(
            LocalDate dateFrom, LocalDate dateTo, int pageNum, int pageSize) {
        if (dateFrom != null && dateTo != null) {
            BusinessAssert.isTrue(!dateFrom.isAfter(dateTo), "dateFrom不能晚于dateTo");
        }
        long offset = offset(pageNum, pageSize);
        List<XxlLearningOrderSummary> rows = orderSummaryMapper.selectPage(dateFrom, dateTo, offset, pageSize);
        long total = orderSummaryMapper.countPage(dateFrom, dateTo);
        return toPage(rows, total, pageNum, pageSize, XxlLearningOrderSummaryVO::from);
    }

    /** 分页查询工作批次。 */
    public PageResult<XxlLearningBatchVO> pageBatches(String status, int pageNum, int pageSize) {
        String normalizedStatus = validateOptionalStatus(status, BATCH_STATUSES, "批次");
        long offset = offset(pageNum, pageSize);
        List<XxlLearningBatch> rows = batchMapper.selectPage(normalizedStatus, offset, pageSize);
        long total = batchMapper.countPage(normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, XxlLearningBatchVO::from);
    }

    /** 分页查询批次工作项。 */
    public PageResult<XxlLearningWorkItemVO> pageWorkItems(
            String batchKey, String status, int pageNum, int pageSize) {
        String normalizedBatchKey = batchKey.trim();
        String normalizedStatus = validateOptionalStatus(status, WORK_ITEM_STATUSES, "工作项");
        long offset = offset(pageNum, pageSize);
        List<XxlLearningWorkItem> rows = workItemMapper.selectPageByBatchKey(
                normalizedBatchKey, normalizedStatus, offset, pageSize);
        long total = workItemMapper.countPageByBatchKey(normalizedBatchKey, normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, XxlLearningWorkItemVO::from);
    }

    /** 分页查询批次处理结果。 */
    public PageResult<XxlLearningWorkResultVO> pageWorkResults(
            String batchKey, int pageNum, int pageSize) {
        String normalizedBatchKey = batchKey.trim();
        long offset = offset(pageNum, pageSize);
        List<XxlLearningWorkResult> rows = workResultMapper.selectPage(normalizedBatchKey, offset, pageSize);
        long total = workResultMapper.countPage(normalizedBatchKey);
        return toPage(rows, total, pageNum, pageSize, XxlLearningWorkResultVO::from);
    }

    private long offset(int pageNum, int pageSize) {
        return (long) (pageNum - 1) * pageSize;
    }

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

    private <S, T> PageResult<T> toPage(
            List<S> rows, long total, int pageNum, int pageSize, Function<S, T> converter) {
        PageResult<T> result = new PageResult<>();
        result.setList(rows.stream().map(converter).toList());
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    private static <E extends Enum<E>> Set<String> enumValues(E[] statuses) {
        return Arrays.stream(statuses)
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
