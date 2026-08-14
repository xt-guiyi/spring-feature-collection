package com.xt.xiaoxingxing.playground.xxljob.controller;

import com.xt.xiaoxingxing.playground.xxljob.service.XxlJobOperationsService;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningBatchVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningExecutionVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningOrderSummaryVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningWorkItemVO;
import com.xt.xiaoxingxing.playground.xxljob.vo.XxlLearningWorkResultVO;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * XXL-JOB 业务结果的只读观察入口。
 *
 * <p>任务的创建、修改、启停和手工触发继续在 XXL-JOB Admin 完成；这里不重复实现管理后台，
 * 只展示 PostgreSQL 中已经提交的执行台账、日报、批次和幂等结果。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/xxl-job/operations")
public class XxlJobOperationsController {

    private final XxlJobOperationsService operationsService;

    /** 对照 Admin 调度日志与业务 executionKey，观察失败重试是否复用了同一条业务执行链。 */
    @GetMapping("/executions")
    public Result<PageResult<XxlLearningExecutionVO>> executions(
            @RequestParam(required = false) @Size(max = 100) String handlerName,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageExecutions(handlerName, status, pageNum, pageSize));
    }

    /** 详情会包含租约、分片、最后错误和调度日志标识，适合排查一次具体执行。 */
    @GetMapping("/executions/{id}")
    public Result<XxlLearningExecutionVO> execution(@PathVariable @Positive long id) {
        return Result.ok(operationsService.getExecution(id));
    }

    /** 日期格式固定为 ISO-8601，例如 2026-08-13；开始和结束日期都包含在筛选范围内。 */
    @GetMapping("/order-summaries")
    public Result<PageResult<XxlLearningOrderSummaryVO>> orderSummaries(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageOrderSummaries(dateFrom, dateTo, pageNum, pageSize));
    }

    /** 查看批次总体状态；状态过滤不传时返回全部批次。 */
    @GetMapping("/batches")
    public Result<PageResult<XxlLearningBatchVO>> batches(
            @RequestParam(required = false) @Size(max = 30) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageBatches(status, pageNum, pageSize));
    }

    /** 查看一个批次内部每个工作项的逻辑桶、尝试次数、租约和最终状态。 */
    @GetMapping("/batches/{batchKey}/items")
    public Result<PageResult<XxlLearningWorkItemVO>> workItems(
            @PathVariable @NotBlank @Size(max = 200) String batchKey,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageWorkItems(batchKey, status, pageNum, pageSize));
    }

    /** 成功结果按批次查询；结果表唯一约束可以直观看出重试没有重复制造业务副作用。 */
    @GetMapping("/results")
    public Result<PageResult<XxlLearningWorkResultVO>> workResults(
            @RequestParam @NotBlank @Size(max = 200) String batchKey,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageWorkResults(batchKey, pageNum, pageSize));
    }
}
