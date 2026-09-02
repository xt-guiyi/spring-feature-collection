package com.xt.xiaoxingxing.playground.features.flowable.service;

import com.xt.xiaoxingxing.playground.features.flowable.constants.FlowableNames;
import com.xt.xiaoxingxing.playground.features.flowable.dto.request.ClaimTaskRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.request.CompleteLeaveTaskRequest;
import com.xt.xiaoxingxing.playground.features.flowable.dto.request.TaskQueryRequest;
import com.xt.xiaoxingxing.playground.features.flowable.entity.FlowableLeaveApproval;
import com.xt.xiaoxingxing.playground.features.flowable.enums.ApprovalDecision;
import com.xt.xiaoxingxing.playground.features.flowable.enums.ApproverRole;
import com.xt.xiaoxingxing.playground.features.flowable.repository.FlowableLeaveApprovalRepository;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableExceptionSupport;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableTaskSupport;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableUserSupport;
import com.xt.xiaoxingxing.playground.features.flowable.support.FlowableVariableSupport;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.FlowableTaskResponse;
import com.xt.xiaoxingxing.playground.features.flowable.dto.response.LeaveRequestResponse;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flowable 人工任务应用服务。
 *
 * <p>这里集中处理任务查询、领取和审批提交；请假申请的创建、业务详情及流程历史仍由
 * {@link FlowableLeaveService} 负责。Controller 不直接调用 Flowable 引擎 API。</p>
 */
@Service
@RequiredArgsConstructor
public class FlowableTaskService {

    private final RuntimeService runtimeService;
    private final TaskService engineTaskService;
    private final FlowableLeaveApprovalRepository approvalRepository;
    private final FlowableUserSupport userSupport;
    private final FlowableVariableSupport variableSupport;
    private final FlowableTaskSupport taskSupport;
    private final FlowableLeaveService leaveService;

    /** 候选人显式领取任务；只有任务候选人可以领取。 */
    @Transactional(transactionManager = "playgroundTransactionManager")
    public FlowableTaskResponse claim(String taskId, ClaimTaskRequest request) {
        Task task = requireActiveTask(taskId);
        userSupport.requireActiveUser(request.getUserId(), "领取人");
        String userId = String.valueOf(request.getUserId());
        if (task.getAssignee() != null) {
            BusinessAssert.isTrue(userId.equals(task.getAssignee()), "任务已被其他人领取");
            return taskSupport.toResponse(task, leaveRequestId(task));
        }
        BusinessAssert.isTrue(taskSupport.isCandidate(task, userId), "当前用户不是该任务候选人");
        FlowableExceptionSupport.call("领取人工任务", () -> {
            engineTaskService.claim(taskId, userId);
            return null;
        });
        Task claimed = requireActiveTask(taskId);
        return taskSupport.toResponse(claimed, leaveRequestId(claimed));
    }

    /** 只有当前 assignee 才能完成任务；先落审批记录，再推进 Flowable。 */
    @Transactional(transactionManager = "playgroundTransactionManager")
    public LeaveRequestResponse complete(String taskId, CompleteLeaveTaskRequest request) {
        Task task = requireActiveTask(taskId);
        userSupport.requireActiveUser(request.getUserId(), "审批人");
        String userId = String.valueOf(request.getUserId());
        BusinessAssert.isTrue(userId.equals(task.getAssignee()), "请先领取任务后再提交审批");

        Long leaveRequestId = leaveRequestId(task);
        String comment = request.getComment() == null ? null : request.getComment().trim();
        ApprovalDecision decision = request.getDecision();
        if (decision == ApprovalDecision.REJECT) {
            BusinessAssert.hasText(comment, "拒绝审批时必须填写审批意见");
        }
        BusinessAssert.isTrue(approvalRepository.findByTaskId(taskId) == null,
                "该任务已经提交审批，不能重复完成");

        FlowableLeaveApproval approval = new FlowableLeaveApproval();
        approval.setLeaveRequestId(leaveRequestId);
        approval.setTaskId(taskId);
        approval.setApproverId(request.getUserId());
        approval.setApproverRole(resolveApproverRole(task, userId));
        approval.setDecision(decision.name());
        approval.setComment(comment);
        approval.setCompletedAt(LocalDateTime.now());
        BusinessAssert.isTrue(approvalRepository.insertIfAbsent(approval),
                "该任务已经提交审批，不能重复完成");

        // 并行任务各自写业务审批记录，汇总 Delegate 在全部任务完成后统一计算最终结果。
        FlowableExceptionSupport.call("完成人工任务", () -> {
            engineTaskService.complete(taskId);
            return null;
        });
        return leaveService.get(leaveRequestId);
    }

    /** 查询当前活动人工任务。 */
    public PageResult<FlowableTaskResponse> tasks(TaskQueryRequest request) {
        TaskQuery query = engineTaskService.createTaskQuery()
                .processDefinitionKey(FlowableNames.PROCESS_DEFINITION_KEY)
                .active()
                .includeIdentityLinks();
        if (request.getCandidateUser() != null && !request.getCandidateUser().isBlank()) {
            query.taskCandidateUser(request.getCandidateUser().trim());
        }
        if (request.getAssignee() != null && !request.getAssignee().isBlank()) {
            query.taskAssignee(request.getAssignee().trim());
        }
        if (request.getProcessInstanceId() != null && !request.getProcessInstanceId().isBlank()) {
            query.processInstanceId(request.getProcessInstanceId().trim());
        }
        if (request.getTaskDefinitionKey() != null && !request.getTaskDefinitionKey().isBlank()) {
            query.taskDefinitionKey(request.getTaskDefinitionKey().trim());
        }
        long offset = (long) (request.getPageNum() - 1) * request.getPageSize();
        long total = FlowableExceptionSupport.call("统计活动人工任务", query::count);
        List<Task> rows = FlowableExceptionSupport.call("查询活动人工任务", () ->
                query.orderByTaskCreateTime().desc().listPage((int) offset, request.getPageSize()));
        PageResult<FlowableTaskResponse> result = new PageResult<>();
        result.setList(rows.stream().map(task -> taskSupport.toResponse(task, leaveRequestId(task))).toList());
        result.setTotal(total);
        result.setPageNum(request.getPageNum());
        result.setPageSize(request.getPageSize());
        return result;
    }

    private Task requireActiveTask(String taskId) {
        BusinessAssert.hasText(taskId, "taskId不能为空");
        Task task = FlowableExceptionSupport.call("查询活动人工任务", () ->
                engineTaskService.createTaskQuery()
                        .taskId(taskId)
                        .active()
                        .includeIdentityLinks()
                        .singleResult());
        return BusinessAssert.notNull(task, "人工任务不存在、已完成或已结束");
    }

    private Long leaveRequestId(Task task) {
        return leaveRequestId(task.getProcessInstanceId());
    }

    private Long leaveRequestId(String processInstanceId) {
        BusinessAssert.hasText(processInstanceId, "任务没有流程实例");
        Object value = FlowableExceptionSupport.call("读取请假流程变量", () ->
                runtimeService.getVariable(processInstanceId, FlowableNames.VAR_LEAVE_REQUEST_ID));
        return variableSupport.asLong(value, FlowableNames.VAR_LEAVE_REQUEST_ID);
    }

    private String resolveApproverRole(Task task, String userId) {
        String taskKey = task.getTaskDefinitionKey();
        if (FlowableNames.TASK_MANAGER_APPROVAL.equals(taskKey)) {
            assertProcessUser(task, userId, FlowableNames.VAR_MANAGER_ID, "经理");
            return ApproverRole.MANAGER.name();
        }
        if (FlowableNames.TASK_HR_APPROVAL.equals(taskKey)) {
            assertProcessUser(task, userId, FlowableNames.VAR_HR_ID, "HR");
            return ApproverRole.HR.name();
        }
        if (FlowableNames.TASK_PARALLEL_APPROVAL.equals(taskKey)) {
            if (sameProcessUser(task, userId, FlowableNames.VAR_MANAGER_ID)) {
                return ApproverRole.MANAGER.name();
            }
            if (sameProcessUser(task, userId, FlowableNames.VAR_HR_ID)) {
                return ApproverRole.HR.name();
            }
            if (sameProcessUser(task, userId, FlowableNames.VAR_LEADER_ID)) {
                return ApproverRole.LEADER.name();
            }
            throw new BusinessException("审批人不是该路线的候选人");
        }
        throw new BusinessException("不支持的审批任务类型: " + taskKey);
    }

    private boolean sameProcessUser(Task task, String userId, String variableName) {
        Object value = FlowableExceptionSupport.call("读取审批人流程变量", () ->
                runtimeService.getVariable(task.getProcessInstanceId(), variableName));
        return value != null && userId.equals(String.valueOf(value));
    }

    private void assertProcessUser(Task task, String userId, String variableName, String role) {
        BusinessAssert.isTrue(sameProcessUser(task, userId, variableName),
                role + "不是当前流程指定审批人");
    }

}
