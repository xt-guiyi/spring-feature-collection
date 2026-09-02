package com.xt.xiaoxingxing.playground.features.flowable.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** flowable_leave_approval 人工任务审计台账；task_id 唯一保证同一任务只落一条决定。 */
@Data
public class FlowableLeaveApproval {

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
