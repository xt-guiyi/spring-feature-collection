package com.xt.xiaoxingxing.playground.features.flowable.controller;

import com.xt.xiaoxingxing.playground.features.flowable.dto.request.LeaveRouteEvaluateRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.FlowableDecisionDefinitionResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.FlowableProcessDefinitionResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.LeaveRouteResponse;
import com.xt.xiaoxingxing.playground.features.flowable.service.FlowableDefinitionService;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Flowable 流程定义、DMN 定义和路由决策的学习接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/flowable")
public class FlowableDefinitionController {

    private final FlowableDefinitionService definitionService;

    /** 查询当前已部署的 BPMN 流程定义。 */
    @GetMapping("/definitions/processes")
    public Result<List<FlowableProcessDefinitionResponse>> processes() {
        return Result.ok(definitionService.listProcessDefinitions());
    }

    /** 查询当前已部署的 DMN 决策定义。 */
    @GetMapping("/definitions/decisions")
    public Result<List<FlowableDecisionDefinitionResponse>> decisions() {
        return Result.ok(definitionService.listDecisionDefinitions());
    }

    /** 直接执行请假 DMN，便于观察规则输出而不启动流程。 */
    @PostMapping("/decisions/leave-route/evaluate")
    public Result<LeaveRouteResponse> evaluate(@Valid @RequestBody LeaveRouteEvaluateRequest request) {
        return Result.ok(definitionService.evaluate(request));
    }
}
