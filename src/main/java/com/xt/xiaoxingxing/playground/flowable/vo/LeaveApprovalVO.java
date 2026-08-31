package com.xt.xiaoxingxing.playground.flowable.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 人工审批业务审计记录。 */
@Data
public class LeaveApprovalVO {

    private Long id;
    private Long leaveRequestId;
    private String taskId;
    private Long approverId;
    private String approverRole;
    private String decision;
    private String comment;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
