package com.xt.xiaoxingxing.playground.flowable.support;

import com.xt.xiaoxingxing.playground.flowable.config.FlowableNames;
import com.xt.xiaoxingxing.playground.flowable.vo.FlowableTaskVO;
import org.flowable.identitylink.api.IdentityLinkInfo;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 统一把 Flowable Task 映射为对外学习视图，并提取候选人身份链接。 */
@Component
public class FlowableTaskSupport {

    public FlowableTaskVO toView(Task task, Long leaveRequestId) {
        FlowableTaskVO view = new FlowableTaskVO();
        view.setId(task.getId());
        view.setName(task.getName());
        view.setTaskDefinitionKey(task.getTaskDefinitionKey());
        view.setProcessInstanceId(task.getProcessInstanceId());
        view.setLeaveRequestId(leaveRequestId);
        view.setAssignee(task.getAssignee());
        view.setState(task.getState());
        view.setApproverRole(roleForTaskKey(task.getTaskDefinitionKey()));
        view.setCreateTime(task.getCreateTime() == null ? null : task.getCreateTime().toInstant());
        view.setClaimTime(task.getClaimTime() == null ? null : task.getClaimTime().toInstant());
        view.setCandidateUsers(candidateUsers(task));
        return view;
    }

    public String roleForTaskKey(String taskDefinitionKey) {
        if (FlowableNames.TASK_MANAGER_APPROVAL.equals(taskDefinitionKey)) {
            return "MANAGER";
        }
        if (FlowableNames.TASK_HR_APPROVAL.equals(taskDefinitionKey)) {
            return "HR";
        }
        if (FlowableNames.TASK_PARALLEL_APPROVAL.equals(taskDefinitionKey)) {
            return "PARALLEL";
        }
        return "UNKNOWN";
    }

    public boolean isCandidate(Task task, String userId) {
        return candidateUsers(task).contains(userId);
    }

    private List<String> candidateUsers(Task task) {
        if (task.getIdentityLinks() == null || task.getIdentityLinks().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> users = new ArrayList<>();
        for (IdentityLinkInfo link : task.getIdentityLinks()) {
            if ("candidate".equalsIgnoreCase(link.getType()) && link.getUserId() != null) {
                users.add(link.getUserId());
            }
        }
        return users;
    }
}
