package com.xt.xiaoxingxing.playground.features.xxljob.service;

import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlBatch;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlExecution;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlOrderSummary;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlWorkItem;
import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlWorkResult;
import com.xt.xiaoxingxing.playground.features.xxljob.enums.XxlBatchStatus;
import com.xt.xiaoxingxing.playground.features.xxljob.enums.XxlExecutionStatus;
import com.xt.xiaoxingxing.playground.features.xxljob.enums.XxlWorkItemStatus;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlBatchMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlExecutionMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlOrderSummaryMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlWorkItemMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.mapper.XxlWorkResultMapper;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.response.XxlBatchResponse;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.response.XxlExecutionResponse;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.response.XxlOrderSummaryResponse;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.response.XxlWorkItemResponse;
import com.xt.xiaoxingxing.playground.features.xxljob.dto.response.XxlWorkResultResponse;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
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

    private static final Set<String> EXECUTION_STATUSES = enumValues(XxlExecutionStatus.values());
    private static final Set<String> BATCH_STATUSES = enumValues(XxlBatchStatus.values());
    private static final Set<String> WORK_ITEM_STATUSES = enumValues(XxlWorkItemStatus.values());

    private final XxlExecutionMapper executionMapper;
    private final XxlOrderSummaryMapper orderSummaryMapper;
    private final XxlBatchMapper batchMapper;
    private final XxlWorkItemMapper workItemMapper;
    private final XxlWorkResultMapper workResultMapper;

    /** 分页查询执行台账。 */
    public PageResult<XxlExecutionResponse> pageExecutions(
            String handlerName, String status, int pageNum, int pageSize) {
        String normalizedHandlerName = normalizeOptional(handlerName);
        String normalizedStatus = validateOptionalStatus(status, EXECUTION_STATUSES, "执行台账");
        long offset = offset(pageNum, pageSize);
        List<XxlExecution> rows = executionMapper.selectPage(
                normalizedHandlerName, normalizedStatus, offset, pageSize);
        long total = executionMapper.countPage(normalizedHandlerName, normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, this::toResponse);
    }

    /** 查询执行台账详情。 */
    public XxlExecutionResponse getExecution(long id) {
        return toResponse(BusinessAssert.notNull(executionMapper.selectById(id), "执行台账不存在"));
    }

    /** 分页查询订单日报。 */
    public PageResult<XxlOrderSummaryResponse> pageOrderSummaries(
            LocalDate dateFrom, LocalDate dateTo, int pageNum, int pageSize) {
        if (dateFrom != null && dateTo != null) {
            BusinessAssert.isTrue(!dateFrom.isAfter(dateTo), "dateFrom不能晚于dateTo");
        }
        long offset = offset(pageNum, pageSize);
        List<XxlOrderSummary> rows = orderSummaryMapper.selectPage(dateFrom, dateTo, offset, pageSize);
        long total = orderSummaryMapper.countPage(dateFrom, dateTo);
        return toPage(rows, total, pageNum, pageSize, this::toResponse);
    }

    /** 分页查询工作批次。 */
    public PageResult<XxlBatchResponse> pageBatches(String status, int pageNum, int pageSize) {
        String normalizedStatus = validateOptionalStatus(status, BATCH_STATUSES, "批次");
        long offset = offset(pageNum, pageSize);
        List<XxlBatch> rows = batchMapper.selectPage(normalizedStatus, offset, pageSize);
        long total = batchMapper.countPage(normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, this::toResponse);
    }

    /** 分页查询批次工作项。 */
    public PageResult<XxlWorkItemResponse> pageWorkItems(
            String batchKey, String status, int pageNum, int pageSize) {
        String normalizedBatchKey = batchKey.trim();
        String normalizedStatus = validateOptionalStatus(status, WORK_ITEM_STATUSES, "工作项");
        long offset = offset(pageNum, pageSize);
        List<XxlWorkItem> rows = workItemMapper.selectPageByBatchKey(
                normalizedBatchKey, normalizedStatus, offset, pageSize);
        long total = workItemMapper.countPageByBatchKey(normalizedBatchKey, normalizedStatus);
        return toPage(rows, total, pageNum, pageSize, this::toResponse);
    }

    /** 分页查询批次处理结果。 */
    public PageResult<XxlWorkResultResponse> pageWorkResults(
            String batchKey, int pageNum, int pageSize) {
        String normalizedBatchKey = batchKey.trim();
        long offset = offset(pageNum, pageSize);
        List<XxlWorkResult> rows = workResultMapper.selectPage(normalizedBatchKey, offset, pageSize);
        long total = workResultMapper.countPage(normalizedBatchKey);
        return toPage(rows, total, pageNum, pageSize, this::toResponse);
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

    private XxlExecutionResponse toResponse(XxlExecution source) {
        XxlExecutionResponse target = new XxlExecutionResponse();
        target.setId(source.getId()); target.setExecutionKey(source.getExecutionKey());
        target.setHandlerName(source.getHandlerName());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        target.setAttemptCount(source.getAttemptCount()); target.setLeaseExpiresAt(source.getLeaseExpiresAt());
        target.setJobId(source.getJobId()); target.setLogId(source.getLogId()); target.setLogDateTime(source.getLogDateTime());
        target.setShardIndex(source.getShardIndex()); target.setShardTotal(source.getShardTotal());
        target.setResultMessage(source.getResultMessage()); target.setLastError(source.getLastError());
        target.setStartedAt(source.getStartedAt()); target.setCompletedAt(source.getCompletedAt());
        target.setCreatedAt(source.getCreatedAt()); target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private XxlBatchResponse toResponse(XxlBatch source) {
        XxlBatchResponse target = new XxlBatchResponse();
        target.setId(source.getId()); target.setBatchKey(source.getBatchKey()); target.setItemCount(source.getItemCount());
        target.setFailEvery(source.getFailEvery()); target.setFailTimes(source.getFailTimes());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        target.setGeneratedExecutionId(source.getGeneratedExecutionId()); target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt()); target.setCompletedAt(source.getCompletedAt());
        return target;
    }

    private XxlOrderSummaryResponse toResponse(XxlOrderSummary source) {
        XxlOrderSummaryResponse target = new XxlOrderSummaryResponse();
        target.setSummaryDate(source.getSummaryDate()); target.setRunVersion(source.getRunVersion());
        target.setOrderCount(source.getOrderCount()); target.setPendingOrderCount(source.getPendingOrderCount());
        target.setPaidOrderCount(source.getPaidOrderCount()); target.setCancelledOrderCount(source.getCancelledOrderCount());
        target.setTotalAmount(source.getTotalAmount()); target.setSourceStartAt(source.getSourceStartAt());
        target.setSourceEndAt(source.getSourceEndAt()); target.setExecutionId(source.getExecutionId());
        target.setCreatedAt(source.getCreatedAt()); target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private XxlWorkItemResponse toResponse(XxlWorkItem source) {
        XxlWorkItemResponse target = new XxlWorkItemResponse();
        target.setId(source.getId()); target.setBatchId(source.getBatchId()); target.setItemNo(source.getItemNo());
        target.setBucketNo(source.getBucketNo()); target.setPlannedFailures(source.getPlannedFailures());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        target.setAttemptCount(source.getAttemptCount()); target.setAvailableAt(source.getAvailableAt());
        target.setLeaseExpiresAt(source.getLeaseExpiresAt()); target.setLastLogId(source.getLastLogId());
        target.setLastError(source.getLastError()); target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt()); target.setCompletedAt(source.getCompletedAt());
        return target;
    }

    private XxlWorkResultResponse toResponse(XxlWorkResult source) {
        XxlWorkResultResponse target = new XxlWorkResultResponse();
        target.setId(source.getId()); target.setWorkItemId(source.getWorkItemId()); target.setBatchId(source.getBatchId());
        target.setItemNo(source.getItemNo()); target.setExecutionId(source.getExecutionId());
        target.setResultValue(source.getResultValue()); target.setJobId(source.getJobId()); target.setLogId(source.getLogId());
        target.setShardIndex(source.getShardIndex()); target.setShardTotal(source.getShardTotal());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }

    private static <E extends Enum<E>> Set<String> enumValues(E[] statuses) {
        return Arrays.stream(statuses)
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
