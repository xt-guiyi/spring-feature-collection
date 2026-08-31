package com.xt.xiaoxingxing.playground.flowable.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** flowable_leave_request 业务台账。Flowable 运行表保存引擎事实，本表保存可查询的业务事实。 */
@Data
public class FlowableLeaveRequest {

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
}
