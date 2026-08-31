package com.xt.xiaoxingxing.playground.flowable.vo;

import lombok.Data;

import java.time.Instant;

/** 流程历史任务摘要，与业务审批台账通过 taskId 对照。 */
@Data
public class FlowableHistoryVO {

    private String id;
    private String name;
    private String taskDefinitionKey;
    private String processInstanceId;
    private String assignee;
    private String completedBy;
    private Instant startTime;
    private Instant endTime;
    private Long durationInMillis;
    private String decision;
    private String comment;
}
