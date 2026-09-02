package com.xt.xiaoxingxing.playground.features.flowable.dto.response;

import lombok.Data;

import java.util.List;

/** 请假流程的 Operations 聚合视图：DMN、Flowable 任务和业务审批记录一次返回。 */
@Data
public class LeaveHistoryResponse {

    private Long leaveRequestId;
    private String requestNo;
    private String processInstanceId;
    private String status;
    private String approvalRoute;
    private String finalDecision;
    private List<FlowableDmnHistoryResponse> decisions;
    private List<FlowableHistoryResponse> tasks;
    private List<LeaveApprovalResponse> approvals;
}
