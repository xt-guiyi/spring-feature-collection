package com.xt.xiaoxingxing.playground.features.flowable.controller;

import com.xt.xiaoxingxing.playground.features.flowable.dto.request.StartLeaveRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.LeaveHistoryResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.LeaveRequestResponse;
import com.xt.xiaoxingxing.playground.features.flowable.service.FlowableLeaveService;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 请假申请、业务台账和 Operations 历史接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/flowable")
public class FlowableLeaveController {

    private final FlowableLeaveService leaveService;

    /** 创建业务申请并启动 Flowable 流程。 */
    @PostMapping("/leaves")
    public Result<LeaveRequestResponse> create(@Valid @RequestBody StartLeaveRequest request) {
        return Result.ok(leaveService.create(request));
    }

    /** 分页查询请假业务台账。 */
    @GetMapping("/leaves")
    public Result<PageResult<LeaveRequestResponse>> page(
            @RequestParam(required = false) @Size(max = 64) String requestNo,
            @RequestParam(required = false) @Positive Long applicantId,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(leaveService.page(requestNo, applicantId, status, pageNum, pageSize));
    }

    /** 查询申请详情、当前任务和审批台账。 */
    @GetMapping("/leaves/{id}")
    public Result<LeaveRequestResponse> get(@PathVariable @Positive long id) {
        return Result.ok(leaveService.get(id));
    }

    /** 查询请假申请的 DMN、任务和业务审批历史；这是请假业务聚合，不是单条任务操作。 */
    @GetMapping("/operations/leaves/{leaveRequestId}/history")
    public Result<LeaveHistoryResponse> history(@PathVariable @Positive long leaveRequestId) {
        return Result.ok(leaveService.operationsHistory(leaveRequestId));
    }
}
