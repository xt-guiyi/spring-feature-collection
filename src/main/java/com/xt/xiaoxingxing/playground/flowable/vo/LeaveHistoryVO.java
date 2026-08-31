package com.xt.xiaoxingxing.playground.flowable.vo;

import lombok.Data;

import java.util.List;

/** 请假流程的 Operations 聚合视图：DMN、Flowable 任务和业务审批记录一次返回。 */
@Data
public class LeaveHistoryVO {

    private Long leaveRequestId;
    private String requestNo;
    private String processInstanceId;
    private String status;
    private String approvalRoute;
    private String finalDecision;
    private List<FlowableDmnHistoryVO> decisions;
    private List<FlowableHistoryVO> tasks;
    private List<LeaveApprovalVO> approvals;
}
