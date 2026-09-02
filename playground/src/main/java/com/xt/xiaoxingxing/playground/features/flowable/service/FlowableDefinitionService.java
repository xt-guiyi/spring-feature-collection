package com.xt.xiaoxingxing.playground.features.flowable.service;

import com.xt.xiaoxingxing.playground.features.flowable.constants.FlowableNames;
import com.xt.xiaoxingxing.playground.features.flowable.dto.request.LeaveRouteEvaluateRequest;
import com.xt.xiaoxingxing.playground.features.flowable.enums.ApprovalRoute;
import com.xt.xiaoxingxing.playground.features.flowable.enums.LeaveType;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableExceptionSupport;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableVariableSupport;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.FlowableDecisionDefinitionResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.FlowableProcessDefinitionResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.LeaveRouteResponse;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.api.ExecuteDecisionBuilder;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flowable 流程定义与 DMN 决策服务。
 */
@Service
@RequiredArgsConstructor
public class FlowableDefinitionService {

    private final RepositoryService repositoryService;
    private final DmnDecisionService dmnDecisionService;
    private final DmnRepositoryService dmnRepositoryService;
    private final FlowableVariableSupport variableSupport;

    /** 查询固定 key 的所有已部署 BPMN 版本。 */
    public List<FlowableProcessDefinitionResponse> listProcessDefinitions() {
        return FlowableExceptionSupport.call("查询BPMN流程定义", () ->
                        repositoryService.createProcessDefinitionQuery()
                                .processDefinitionKey(FlowableNames.PROCESS_DEFINITION_KEY)
                                .orderByProcessDefinitionVersion()
                                .desc()
                                .list())
                .stream()
                .map(this::toView)
                .toList();
    }

    /** 查询固定 key 的所有已部署 DMN 版本。 */
    public List<FlowableDecisionDefinitionResponse> listDecisionDefinitions() {
        List<DmnDecision> decisions = FlowableExceptionSupport.call("查询DMN定义", () ->
                dmnRepositoryService.createDecisionQuery()
                        .decisionKey(FlowableNames.DECISION_KEY)
                        .orderByDecisionVersion()
                        .desc()
                        .list());
        return decisions.stream().map(this::toDefinitionView).toList();
    }

    /** 执行请假路由决策并返回标准化的 DMN 结果。 */
    public LeaveRouteResponse evaluate(LeaveRouteEvaluateRequest request) {
        LeaveType leaveType = request.getLeaveType() == null ? LeaveType.ANNUAL : request.getLeaveType();
        return evaluate(request.getLeaveDays(), leaveType.name());
    }

    /** 供 BPMN Delegate 与 HTTP 接口共用的决策入口。 */
    public LeaveRouteResponse evaluate(Integer leaveDays, String leaveType) {
        return evaluate(leaveDays, leaveType, null, null, null);
    }

    /** 在 BPMN 服务任务内执行 DMN，并把执行上下文写入 DMN 历史。 */
    public LeaveRouteResponse evaluateForProcess(Integer leaveDays, String leaveType,
                                           String instanceId, String executionId, String activityId) {
        return evaluate(leaveDays, leaveType, instanceId, executionId, activityId);
    }



    private LeaveRouteResponse evaluate(Integer leaveDays, String leaveType,
                                  String instanceId, String executionId, String activityId) {
        // 步骤1：把请假类型统一转换为 DMN 规则中声明的枚举文本。
        String normalizedType = normalizeLeaveType(leaveType);

        // 步骤2：按照 DMN 输入列名称组装决策参数。
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(FlowableNames.VAR_LEAVE_DAYS, leaveDays);
        inputs.put(FlowableNames.VAR_LEAVE_TYPE, normalizedType);

        // 步骤3：创建 DMN 执行器，并绑定决策表 key 和输入参数。
        Map<String, Object> result = FlowableExceptionSupport.call("执行请假路由DMN",
                () -> {
                    ExecuteDecisionBuilder builder = dmnDecisionService.createExecuteDecisionBuilder()
                            .decisionKey(FlowableNames.DECISION_KEY)
                            .variables(inputs);
                    // 步骤4：流程内执行时补充 BPMN 上下文，让 DMN 历史关联到当前流程节点。
                    if (instanceId != null) {
                        builder.instanceId(instanceId)
                                .executionId(executionId)
                                .activityId(activityId)
                                .scopeType(ScopeTypes.BPMN);
                    }
                    return builder.executeDecisionWithSingleResult();
                });
        // 步骤5：确认 DMN 确实返回了一条决策结果。
        BusinessAssert.notNull(result, "DMN未返回请假审批路线");

        // 步骤6：读取审批路线；兼容部分设计工具使用 route 作为输出列名。
        Object routeValue = result.get(FlowableNames.VAR_APPROVAL_ROUTE);
        if (routeValue == null) {
            // 兼容设计工具把输出列命名为 route 的模型，但对外仍统一 approvalRoute。
            routeValue = result.get("route");
        }
        BusinessAssert.notNull(routeValue, "DMN结果缺少approvalRoute输出");
        ApprovalRoute route = parseRoute(String.valueOf(routeValue));

        // 步骤7：把 DMN 输出和本次输入组装成对外返回对象。
        LeaveRouteResponse view = new LeaveRouteResponse();
        view.setLeaveDays(leaveDays);
        view.setLeaveType(normalizedType);
        view.setApprovalRoute(route.name());
        // 步骤8：读取可选的角色说明和所需审批人数。
        Object roles = result.get("requiredRoles");
        if (roles != null) {
            view.setRequiredRoles(String.valueOf(roles));
        }
        Object requiredCount = result.get(FlowableNames.VAR_REQUIRED_APPROVAL_COUNT);
        if (requiredCount != null) {
            view.setRequiredApprovalCount(variableSupport.asInteger(
                    requiredCount, FlowableNames.VAR_REQUIRED_APPROVAL_COUNT));
        }
        return view;
    }

    /** 将输入规范化为 DMN 资源中声明的枚举文本。 */
    public String normalizeLeaveType(String leaveType) {
        BusinessAssert.notNull(leaveType, "leaveType不能为空");
        String normalized = leaveType.trim().toUpperCase(Locale.ROOT);
        try {
            return LeaveType.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("leaveType仅支持ANNUAL、SICK、PERSONAL");
        }
    }

    private ApprovalRoute parseRoute(String value) {
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return ApprovalRoute.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("DMN返回未知审批路线: " + value);
        }
    }

    private FlowableProcessDefinitionResponse toView(ProcessDefinition source) {
        FlowableProcessDefinitionResponse view = new FlowableProcessDefinitionResponse();
        view.setId(source.getId());
        view.setKey(source.getKey());
        view.setName(source.getName());
        view.setVersion(source.getVersion());
        view.setResourceName(source.getResourceName());
        view.setDeploymentId(source.getDeploymentId());
        view.setCategory(source.getCategory());
        view.setTenantId(source.getTenantId());
        view.setSuspended(source.isSuspended());
        return view;
    }

    private FlowableDecisionDefinitionResponse toDefinitionView(DmnDecision source) {
        FlowableDecisionDefinitionResponse view = new FlowableDecisionDefinitionResponse();
        view.setId(source.getId());
        view.setKey(source.getKey());
        view.setName(source.getName());
        view.setVersion(source.getVersion());
        view.setResourceName(source.getResourceName());
        view.setDeploymentId(source.getDeploymentId());
        view.setCategory(source.getCategory());
        view.setTenantId(source.getTenantId());
        view.setDecisionType(source.getDecisionType());
        return view;
    }
}
