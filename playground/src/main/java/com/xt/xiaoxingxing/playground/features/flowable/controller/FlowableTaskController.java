package com.xt.xiaoxingxing.playground.features.flowable.controller;

import com.xt.xiaoxingxing.playground.features.flowable.dto.request.ClaimTaskRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.request.CompleteLeaveTaskRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.request.TaskQueryRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.FlowableTaskResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.LeaveRequestResponse;
import com.xt.xiaoxingxing.playground.features.flowable.service.FlowableTaskService;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Flowable 人工任务查询、领取和完成接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/flowable")
public class FlowableTaskController {

    private final FlowableTaskService taskService;

    /** 分页查询当前活动任务。 */
    @GetMapping("/tasks")
    public Result<PageResult<FlowableTaskResponse>> tasks(@Valid @ModelAttribute TaskQueryRequest request) {
        return Result.ok(taskService.tasks(request));
    }

    /** 候选人领取任务。 */
    @PostMapping("/tasks/{taskId}/claim")
    public Result<FlowableTaskResponse> claim(
            @PathVariable String taskId,
            @Valid @RequestBody ClaimTaskRequest request) {
        return Result.ok(taskService.claim(taskId, request));
    }

    /** 当前办理人提交审批决定。 */
    @PostMapping("/tasks/{taskId}/complete")
    public Result<LeaveRequestResponse> complete(
            @PathVariable String taskId,
            @Valid @RequestBody CompleteLeaveTaskRequest request) {
        return Result.ok(taskService.complete(taskId, request));
    }
}
