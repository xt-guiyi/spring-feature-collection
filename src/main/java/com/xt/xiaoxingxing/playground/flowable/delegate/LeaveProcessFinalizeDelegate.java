package com.xt.xiaoxingxing.playground.flowable.delegate;

import com.xt.xiaoxingxing.playground.flowable.config.FlowableNames;
import com.xt.xiaoxingxing.playground.flowable.enums.ApprovalDecision;
import com.xt.xiaoxingxing.playground.flowable.enums.LeaveStatus;
import com.xt.xiaoxingxing.playground.flowable.repository.FlowableLeaveRequestRepository;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableVariableSupport;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** BPMN 结束前服务任务：把流程决定同步到业务台账。 */
@Component("leaveProcessFinalizeDelegate")
@RequiredArgsConstructor
public class LeaveProcessFinalizeDelegate implements JavaDelegate {

    private final FlowableLeaveRequestRepository requestRepository;
    private final FlowableVariableSupport variableSupport;

    @Override
    public void execute(DelegateExecution execution) {
        // 第1步：读取流程关联的业务申请 ID，后续要更新这条申请记录。
        Long leaveRequestId = variableSupport.asLong(
                execution.getVariable(FlowableNames.VAR_LEAVE_REQUEST_ID),
                FlowableNames.VAR_LEAVE_REQUEST_ID);

        // 第2步：读取聚合节点写入的最终审批决定。
        Object rawDecision = execution.getVariable(FlowableNames.VAR_FINAL_DECISION);

        // 第3步：最终决定是必需的；没有它就不能把流程结果同步到业务表。
        BusinessAssert.notNull(rawDecision, "流程变量缺少finalDecision");

        // 第4步：统一转换为大写，兼容流程变量中的大小写差异。
        String decision = String.valueOf(rawDecision).toUpperCase(Locale.ROOT);

        // 第5步：只允许 APPROVE 或 REJECT，避免非法值污染申请状态。
        BusinessAssert.isTrue(ApprovalDecision.APPROVE.name().equals(decision)
                        || ApprovalDecision.REJECT.name().equals(decision),
                "流程变量finalDecision不是有效审批决定");

        // 第6步：读取驳回意见；通过时通常没有意见，因此允许为空。
        String comment = execution.getVariable(FlowableNames.VAR_FINAL_COMMENT) == null
                ? null : String.valueOf(execution.getVariable(FlowableNames.VAR_FINAL_COMMENT));

        // 第7步：把流程层的审批决定转换成业务申请表使用的最终状态。
        String status = ApprovalDecision.REJECT.name().equals(decision)
                ? LeaveStatus.REJECTED.name() : LeaveStatus.APPROVED.name();

        // 第8步：将状态、最终决定和审批意见一次性写回业务申请台账。
        BusinessAssert.isTrue(
                requestRepository.finalize(leaveRequestId, status, decision, comment),
                "请假申请不存在或已经被其他流程推进");
    }

}
