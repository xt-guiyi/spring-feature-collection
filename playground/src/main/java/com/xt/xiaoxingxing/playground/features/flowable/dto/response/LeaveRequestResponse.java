package com.xt.xiaoxingxing.playground.features.flowable.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 请假流程详情，汇合业务台账、当前 Flowable 任务和人工审批记录。 */
@Data
public class LeaveRequestResponse {

    private Long id;
    private String requestNo;
    private Long applicantId;
    private Long managerId;
    private Long hrId;
    private Long leaderId;
    private Integer leaveDays;
    private String leaveType;
    private String reason;
    private String approvalRoute;
    private String processInstanceId;
    private String status;
    private String finalDecision;
    private String finalComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LeaveApprovalResponse> approvals;
    private List<FlowableTaskResponse> activeTasks;
}
