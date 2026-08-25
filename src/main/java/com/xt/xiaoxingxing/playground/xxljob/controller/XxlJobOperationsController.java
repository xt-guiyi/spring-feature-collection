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

/** 查询 XXL-JOB 学习任务的业务结果。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/xxl-job/operations")
public class XxlJobOperationsController {

    private final XxlJobOperationsService operationsService;

    /** 分页查询执行台账。 */
    @GetMapping("/executions")
    public Result<PageResult<XxlLearningExecutionVO>> executions(
            @RequestParam(required = false) @Size(max = 100) String handlerName,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageExecutions(handlerName, status, pageNum, pageSize));
    }

    /** 查询执行台账详情。 */
    @GetMapping("/executions/{id}")
    public Result<XxlLearningExecutionVO> execution(@PathVariable @Positive long id) {
        return Result.ok(operationsService.getExecution(id));
    }

    /** 分页查询订单日报。 */
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

    /** 分页查询工作批次。 */
    @GetMapping("/batches")
    public Result<PageResult<XxlLearningBatchVO>> batches(
            @RequestParam(required = false) @Size(max = 30) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageBatches(status, pageNum, pageSize));
    }

    /** 分页查询批次工作项。 */
    @GetMapping("/batches/{batchKey}/items")
    public Result<PageResult<XxlLearningWorkItemVO>> workItems(
            @PathVariable @NotBlank @Size(max = 200) String batchKey,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageWorkItems(batchKey, status, pageNum, pageSize));
    }

    /** 分页查询批次处理结果。 */
    @GetMapping("/results")
    public Result<PageResult<XxlLearningWorkResultVO>> workResults(
            @RequestParam @NotBlank @Size(max = 200) String batchKey,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(operationsService.pageWorkResults(batchKey, pageNum, pageSize));
    }
}
