package com.xt.xiaoxingxing.playground.flowable.service;

import com.xt.xiaoxingxing.playground.flowable.config.FlowableNames;
import com.xt.xiaoxingxing.playground.flowable.dto.request.StartLeaveRequest;
import com.xt.xiaoxingxing.playground.flowable.entity.FlowableLeaveApproval;
import com.xt.xiaoxingxing.playground.flowable.entity.FlowableLeaveRequest;
import com.xt.xiaoxingxing.playground.flowable.enums.LeaveStatus;
import com.xt.xiaoxingxing.playground.flowable.repository.FlowableLeaveApprovalRepository;
import com.xt.xiaoxingxing.playground.flowable.repository.FlowableLeaveRequestRepository;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableApprovalUserResolver;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableExceptionSupport;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableTaskSupport;
import com.xt.xiaoxingxing.playground.flowable.support.FlowableUserSupport;
import com.xt.xiaoxingxing.playground.flowable.vo.FlowableHistoryVO;
import com.xt.xiaoxingxing.playground.flowable.vo.FlowableDmnHistoryVO;
import com.xt.xiaoxingxing.playground.flowable.vo.LeaveApprovalVO;
import com.xt.xiaoxingxing.playground.flowable.vo.LeaveHistoryVO;
import com.xt.xiaoxingxing.playground.flowable.vo.LeaveRequestVO;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.dmn.api.DmnHistoricDecisionExecution;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 请假流程应用服务。
 *
 * <p>Flowable 运行时负责流程推进，flowable_leave_* 只保存面向业务查询的事实和审批审计。
 * 本类只处理请假申请、请假详情和请假历史；人工任务由 {@link FlowableTaskService} 负责。</p>
 */
@Service
@RequiredArgsConstructor
public class FlowableLeaveService {

    private final RuntimeService runtimeService;
    private final TaskService engineTaskService;
    private final HistoryService historyService;
    private final DmnHistoryService dmnHistoryService;
    private final FlowableLeaveRequestRepository requestRepository;
    private final FlowableLeaveApprovalRepository approvalRepository;
    private final FlowableUserSupport userSupport;
    private final FlowableApprovalUserResolver approvalUserResolver;
    private final FlowableTaskSupport taskSupport;

    /** 校验申请、写业务台账，并启动由 Delegate 执行 DMN 的 BPMN 流程。 */
    @Transactional(transactionManager = "playgroundTransactionManager")
    public LeaveRequestVO create(StartLeaveRequest request) {
        // 步骤1：统一去除申请单号、请假原因两端的空白字符；字段格式由 DTO 校验。
        String requestNo = request.getRequestNo().trim();
        String reason = request.getReason() == null ? null : request.getReason().trim();
        String leaveType = request.getLeaveType() == null ? null : request.getLeaveType().name();

        // 步骤2：按 requestNo 做幂等校验；相同载荷返回原申请，不同载荷拒绝覆盖。
        FlowableLeaveRequest existing = requestRepository.findByRequestNo(requestNo);
        if (existing != null) {
            BusinessAssert.isTrue(samePayload(existing, request, leaveType, reason),
                    "requestNo已经存在，但请求载荷不一致");
            return get(existing.getId());
        }

        // 步骤3：确认申请人存在于 users 表且状态为 ACTIVE。
        userSupport.requireActiveUser(request.getApplicantId(), "申请人");
        Long managerId = FlowableApprovalUserResolver.MANAGER_ID;

        // 步骤4：创建 RUNNING 状态的业务申请台账；路线和全部审批人由流程内 Delegate 校验并回写。
        FlowableLeaveRequest entity = new FlowableLeaveRequest();
        entity.setRequestNo(requestNo);
        entity.setApplicantId(request.getApplicantId());
        entity.setManagerId(managerId);
        entity.setLeaveDays(request.getLeaveDays());
        entity.setLeaveType(leaveType);
        entity.setReason(reason);
        entity.setStatus(LeaveStatus.RUNNING.name());
        BusinessAssert.isTrue(requestRepository.insert(entity), "请假申请保存失败");

        // 步骤5：只传入申请业务变量；审批人不来自请求，由流程 Delegate 从服务端解析。
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(FlowableNames.VAR_LEAVE_REQUEST_ID, entity.getId());
        variables.put(FlowableNames.VAR_LEAVE_DAYS, request.getLeaveDays());
        variables.put(FlowableNames.VAR_LEAVE_TYPE, leaveType);

        // 步骤6：按固定流程 key 启动 BPMN；引擎会自动执行 determineApprovalRoute Delegate，DMN 只在流程内执行一次。
        ProcessInstance processInstance = FlowableExceptionSupport.call("启动请假审批流程", () ->
                runtimeService.createProcessInstanceBuilder()
                        .processDefinitionKey(FlowableNames.PROCESS_DEFINITION_KEY)
                        .businessKey(requestNo)
                        .name("请假审批-" + requestNo)
                        .variables(variables)
                        .start());

        // 步骤7：把 Flowable 流程实例 ID 回写业务申请，再返回包含当前任务的完整详情。
        BusinessAssert.isTrue(
                requestRepository.attachProcessInstance(entity.getId(), processInstance.getId()),
                "请假申请不存在或已经绑定流程实例");
        return get(entity.getId());
    }

    /** 查询业务详情，并汇合当前人工任务与审批审计记录。 */
    public LeaveRequestVO get(Long id) {
        FlowableLeaveRequest source = requestRepository.requireById(id);
        LeaveRequestVO view = toView(source);
        view.setApprovals(approvalRepository.findByLeaveRequestId(id).stream()
                .map(this::toApprovalView).toList());
        if (source.getProcessInstanceId() == null) {
            view.setActiveTasks(Collections.emptyList());
            return view;
        }
        List<Task> tasks = FlowableExceptionSupport.call("查询请假申请当前任务", () ->
                engineTaskService.createTaskQuery()
                        .processInstanceId(source.getProcessInstanceId())
                        .active()
                        .includeIdentityLinks()
                        .orderByTaskCreateTime().asc()
                        .list());
        view.setActiveTasks(tasks.stream().map(task -> taskSupport.toView(task, id)).toList());
        return view;
    }

    /** 可选的业务申请分页查询，供后续页面直接复用。 */
    public PageResult<LeaveRequestVO> page(
            String requestNo, Long applicantId, String status, int pageNum, int pageSize) {
        String normalizedRequestNo = normalizeOptional(requestNo);
        String normalizedStatus = normalizeStatus(status);
        List<FlowableLeaveRequest> rows = requestRepository.page(
                normalizedRequestNo, applicantId, normalizedStatus, pageNum, pageSize);
        PageResult<LeaveRequestVO> result = new PageResult<>();
        result.setList(rows.stream().map(this::toView).toList());
        result.setTotal(requestRepository.count(normalizedRequestNo, applicantId, normalizedStatus));
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    /** 查询一个流程实例的 DMN、人工任务和业务审批历史。 */
    public LeaveHistoryVO operationsHistory(Long leaveRequestId) {
        FlowableLeaveRequest request = requestRepository.requireById(leaveRequestId);
        BusinessAssert.hasText(request.getProcessInstanceId(), "请假申请尚未绑定流程实例");
        String processInstanceId = request.getProcessInstanceId();
        List<HistoricTaskInstance> taskRows = FlowableExceptionSupport.call("查询流程任务历史", () ->
                historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .orderByHistoricTaskInstanceStartTime().asc()
                        .list());
        List<DmnHistoricDecisionExecution> decisionRows = FlowableExceptionSupport.call("查询DMN执行历史", () ->
                dmnHistoryService.createHistoricDecisionExecutionQuery()
                        .instanceId(processInstanceId)
                        .decisionKey(FlowableNames.DECISION_KEY)
                        .orderByStartTime().asc()
                        .list());
        LeaveHistoryVO view = new LeaveHistoryVO();
        view.setLeaveRequestId(request.getId());
        view.setRequestNo(request.getRequestNo());
        view.setProcessInstanceId(processInstanceId);
        view.setStatus(request.getStatus());
        view.setApprovalRoute(request.getApprovalRoute());
        view.setFinalDecision(request.getFinalDecision());
        view.setDecisions(decisionRows.stream().map(this::toDmnHistoryView).toList());
        view.setTasks(taskRows.stream().map(this::toHistoryView).toList());
        view.setApprovals(approvalRepository.findByLeaveRequestId(leaveRequestId).stream()
                .map(this::toApprovalView).toList());
        return view;
    }

    private boolean samePayload(FlowableLeaveRequest existing, StartLeaveRequest request,
                                String leaveType, String reason) {
        return Objects.equals(existing.getApplicantId(), request.getApplicantId())
                && Objects.equals(existing.getLeaveDays(), request.getLeaveDays())
                && Objects.equals(existing.getLeaveType(), leaveType)
                && Objects.equals(existing.getReason(), reason);
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeOptional(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        try {
            return LeaveStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new com.xt.xiaoxingxing.shared.exception.BusinessException(
                    "status仅支持RUNNING、APPROVED、REJECTED");
        }
    }

    private String normalizeOptional(String value) {
        return BusinessAssert.hasText(value) ? value.trim() : null;
    }

    private LeaveRequestVO toView(FlowableLeaveRequest source) {
        LeaveRequestVO view = new LeaveRequestVO();
        view.setId(source.getId());
        view.setRequestNo(source.getRequestNo());
        view.setApplicantId(source.getApplicantId());
        view.setManagerId(source.getManagerId());
        view.setHrId(source.getHrId());
        view.setLeaderId(source.getLeaderId());
        view.setLeaveDays(source.getLeaveDays());
        view.setLeaveType(source.getLeaveType());
        view.setReason(source.getReason());
        view.setApprovalRoute(source.getApprovalRoute());
        view.setProcessInstanceId(source.getProcessInstanceId());
        view.setStatus(source.getStatus());
        view.setFinalDecision(source.getFinalDecision());
        view.setFinalComment(source.getFinalComment());
        view.setCreatedAt(source.getCreatedAt());
        view.setUpdatedAt(source.getUpdatedAt());
        return view;
    }

    private LeaveApprovalVO toApprovalView(FlowableLeaveApproval source) {
        LeaveApprovalVO view = new LeaveApprovalVO();
        view.setId(source.getId());
        view.setLeaveRequestId(source.getLeaveRequestId());
        view.setTaskId(source.getTaskId());
        view.setApproverId(source.getApproverId());
        view.setApproverRole(source.getApproverRole());
        view.setDecision(source.getDecision());
        view.setComment(source.getComment());
        view.setCompletedAt(source.getCompletedAt());
        view.setCreatedAt(source.getCreatedAt());
        return view;
    }

    private FlowableHistoryVO toHistoryView(HistoricTaskInstance source) {
        FlowableHistoryVO view = new FlowableHistoryVO();
        view.setId(source.getId());
        view.setName(source.getName());
        view.setTaskDefinitionKey(source.getTaskDefinitionKey());
        view.setProcessInstanceId(source.getProcessInstanceId());
        view.setAssignee(source.getAssignee());
        view.setCompletedBy(source.getCompletedBy());
        view.setStartTime(source.getStartTime() == null ? null : source.getStartTime().toInstant());
        view.setEndTime(source.getEndTime() == null ? null : source.getEndTime().toInstant());
        view.setDurationInMillis(source.getDurationInMillis());
        FlowableLeaveApproval approval = approvalRepository.findByTaskId(source.getId());
        if (approval != null) {
            view.setDecision(approval.getDecision());
            view.setComment(approval.getComment());
        }
        return view;
    }

    private FlowableDmnHistoryVO toDmnHistoryView(DmnHistoricDecisionExecution source) {
        FlowableDmnHistoryVO view = new FlowableDmnHistoryVO();
        view.setId(source.getId());
        view.setDecisionKey(source.getDecisionKey());
        view.setDecisionName(source.getDecisionName());
        view.setDecisionVersion(source.getDecisionVersion());
        view.setDecisionDefinitionId(source.getDecisionDefinitionId());
        view.setDeploymentId(source.getDeploymentId());
        view.setInstanceId(source.getInstanceId());
        view.setExecutionId(source.getExecutionId());
        view.setActivityId(source.getActivityId());
        view.setScopeType(source.getScopeType());
        view.setFailed(source.isFailed());
        view.setStartTime(source.getStartTime() == null ? null : source.getStartTime().toInstant());
        view.setEndTime(source.getEndTime() == null ? null : source.getEndTime().toInstant());
        view.setExecutionJson(source.getExecutionJson());
        return view;
    }
}
