package com.xt.xiaoxingxing.playground.features.flowable.delegate;

import com.xt.xiaoxingxing.playground.features.flowable.constants.FlowableNames;
import com.xt.xiaoxingxing.playground.features.flowable.entity.FlowableLeaveApproval;
import com.xt.xiaoxingxing.playground.features.flowable.enums.ApprovalDecision;
import com.xt.xiaoxingxing.playground.features.flowable.repository.FlowableLeaveApprovalRepository;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableVariableSupport;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 并行多实例完成后的聚合服务任务。
 *
 * <p>每个并行任务只写自己的业务审批记录，不能用共享的 finalDecision 变量互相覆盖。
 * 多实例全部完成后，以业务表中的审批记录为事实：任一拒绝则拒绝，否则整体通过。</p>
 */
@Component("leaveApprovalAggregateDelegate")
@RequiredArgsConstructor
public class LeaveApprovalAggregateDelegate implements JavaDelegate {

    private final FlowableLeaveApprovalRepository approvalRepository;
    private final FlowableVariableSupport variableSupport;

    @Override
    public void execute(DelegateExecution execution) {
        // 第1步：读取当前流程对应的业务申请 ID，用它查询所有审批记录。
        Long leaveRequestId = variableSupport.asLong(
                execution.getVariable(FlowableNames.VAR_LEAVE_REQUEST_ID),
                FlowableNames.VAR_LEAVE_REQUEST_ID);

        // 第2步：从业务审批表读取经理、HR、负责人等并行任务的处理结果。
        List<FlowableLeaveApproval> approvals = approvalRepository.findByLeaveRequestId(leaveRequestId);

        // 第3步：读取 DMN 路由阶段写入的必需审批人数。
        int requiredCount = variableSupport.asInteger(
                execution.getVariable(FlowableNames.VAR_REQUIRED_APPROVAL_COUNT),
                FlowableNames.VAR_REQUIRED_APPROVAL_COUNT);

        // 第4步：防止流程变量被错误配置为零或负数。
        BusinessAssert.isTrue(requiredCount >= 1, "流程变量requiredApprovalCount无效");

        // 第5步：确认所有并行审批记录都已经落库，再开始汇总最终结果。
        BusinessAssert.isTrue(approvals.size() >= requiredCount,
                "审批记录尚未全部落库，无法聚合最终决定");

        // 第6步：查找是否存在任意一条驳回记录；驳回记录优先于其他审批结果。
        FlowableLeaveApproval rejected = approvals.stream()
                .filter(item -> ApprovalDecision.REJECT.name().equals(item.getDecision()))
                .findFirst()
                .orElse(null);

        // 第7步：有驳回就是整体驳回，全部通过（且没有驳回）才算整体通过。
        String decision = rejected == null ? ApprovalDecision.APPROVE.name() : ApprovalDecision.REJECT.name();


        // 第9步：同时保存最终决定变量，供流程历史和后续服务任务读取。
        execution.setVariable(FlowableNames.VAR_FINAL_DECISION, decision);

        // 第10步：如果最终是驳回，把驳回人的意见一并传给后续节点。
        if (rejected != null && rejected.getComment() != null) {
            execution.setVariable(FlowableNames.VAR_FINAL_COMMENT, rejected.getComment());
        }
    }

}
