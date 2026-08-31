package com.xt.xiaoxingxing.playground.flowable.delegate;

import com.xt.xiaoxingxing.playground.flowable.config.FlowableNames;
import com.xt.xiaoxingxing.playground.flowable.enums.ApprovalRoute;
import com.xt.xiaoxingxing.playground.flowable.repository.FlowableLeaveRequestRepository;
import com.xt.xiaoxingxing.playground.flowable.service.FlowableDefinitionService;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableApprovalUserResolver;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableUserSupport;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableVariableSupport;
import com.xt.xiaoxingxing.playground.flowable.vo.LeaveRouteVO;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** BPMN 服务任务：执行一次 DMN，解析服务端审批人，并把路线和候选人写回流程。 */
@Component("leaveRouteDecisionDelegate")
@RequiredArgsConstructor
public class LeaveRouteDecisionDelegate implements JavaDelegate {

    private final FlowableDefinitionService definitionService;
    private final FlowableApprovalUserResolver approvalUserResolver;
    private final FlowableUserSupport userSupport;
    private final FlowableVariableSupport variableSupport;
    private final FlowableLeaveRequestRepository requestRepository;

    @Override
    public void execute(DelegateExecution execution) {
        // 第1步：从流程变量中读取请假天数；DMN 会根据它决定审批路线。
        Integer leaveDays = variableSupport.asInteger(
                execution.getVariable(FlowableNames.VAR_LEAVE_DAYS),
                FlowableNames.VAR_LEAVE_DAYS);

        // 第2步：读取请假类型，并作为 DMN 的另一个输入。
        String leaveType = String.valueOf(execution.getVariable(FlowableNames.VAR_LEAVE_TYPE));

        // 第3步：执行请假路线 DMN；同时传入流程上下文，让 DMN 执行进入 Flowable 历史。
        LeaveRouteVO decision = definitionService.evaluateForProcess(
                leaveDays,
                leaveType,
                execution.getProcessInstanceId(),
                execution.getId(),
                execution.getCurrentActivityId());

        // 第4步：把 DMN 返回的路线文本转换成业务枚举，避免后续使用任意字符串。
        String route = decision.getApprovalRoute();
        ApprovalRoute approvalRoute = ApprovalRoute.valueOf(route);

        // 第5步：按照路线读取服务端固定的经理、HR、负责人审批人配置。
        FlowableApprovalUserResolver.ResolvedApprovers resolvedApprovers =
                approvalUserResolver.resolve(approvalRoute);

        // 第6步：只校验当前路线需要的审批人存在且为 ACTIVE，并生成候选人 ID 列表。
        List<String> approverIds = requireActiveApprovers(approvalRoute, resolvedApprovers);

        // 第7步：把审批路线写入流程变量，供网关表达式判断走哪条 BPMN 分支。
        execution.setVariable(FlowableNames.VAR_APPROVAL_ROUTE, route);

        // 第8步：写入经理候选人；经理是所有路线都必需的审批人。
        execution.setVariable(FlowableNames.VAR_MANAGER_ID,
                String.valueOf(resolvedApprovers.managerId()));

        // 第9步：路线需要 HR 时才写入 HR 流程变量。
        if (resolvedApprovers.hrId() != null) {
            execution.setVariable(FlowableNames.VAR_HR_ID,
                    String.valueOf(resolvedApprovers.hrId()));
        }

        // 第10步：路线需要负责人时才写入负责人流程变量。
        if (resolvedApprovers.leaderId() != null) {
            execution.setVariable(FlowableNames.VAR_LEADER_ID,
                    String.valueOf(resolvedApprovers.leaderId()));
        }

        // 第11步：写入并行多实例节点要遍历的候选人集合，Flowable 会据此创建任务。
        execution.setVariable(FlowableNames.VAR_APPROVAL_USER_IDS, approverIds);

        // 第12步：校验 DMN 规定的审批人数与服务端实际候选人数一致。
        Integer dmnCount = decision.getRequiredApprovalCount();
        BusinessAssert.isTrue(dmnCount == null || dmnCount == approverIds.size(),
                "DMN审批人数与流程候选人数量不一致");

        // 第13步：保存最终审批人数，供多实例汇总节点使用。
        execution.setVariable(FlowableNames.VAR_REQUIRED_APPROVAL_COUNT,
                dmnCount == null ? approverIds.size() : dmnCount);

        // 第14步：读取业务申请 ID，用它把流程实例和业务申请表关联起来。
        Long leaveRequestId = variableSupport.asLong(
                execution.getVariable(FlowableNames.VAR_LEAVE_REQUEST_ID),
                FlowableNames.VAR_LEAVE_REQUEST_ID);

        // 第15步：将 DMN 得出的路线和审批人写回业务申请台账，供详情和历史接口查询。
        BusinessAssert.isTrue(requestRepository.updateApproversAndRoute(
                leaveRequestId,
                resolvedApprovers.managerId(),
                resolvedApprovers.hrId(),
                resolvedApprovers.leaderId(),
                route), "请假审批人和路由保存失败");
    }

    /**
     * 只校验当前 DMN 路线真正需要的服务端审批人；未参与当前路线的固定用户
     * 不会阻止流程启动，例如两天请假不要求负责人用户存在。
     */
    private List<String> requireActiveApprovers(
            ApprovalRoute route, FlowableApprovalUserResolver.ResolvedApprovers approvers) {
        List<Long> ids = new ArrayList<>();
        addRequired(ids, approvers.managerId(), "经理");
        if (route == ApprovalRoute.MANAGER_HR || route == ApprovalRoute.MANAGER_HR_LEADER) {
            addRequired(ids, approvers.hrId(), "HR");
        }
        if (route == ApprovalRoute.MANAGER_HR_LEADER) {
            addRequired(ids, approvers.leaderId(), "负责人");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        BusinessAssert.isTrue(uniqueIds.size() == ids.size(), "服务端配置的审批人不能重复");
        return ids.stream().map(String::valueOf).toList();
    }

    private void addRequired(List<Long> ids, Long userId, String role) {
        userSupport.requireActiveUser(userId, role);
        ids.add(userId);
    }

}
