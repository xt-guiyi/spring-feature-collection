package com.xt.xiaoxingxing.playground.features.flowable.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** 当前人工任务摘要；candidateUsers 直接展示候选人模式，assignee 为空表示尚未领取。 */
@Data
public class FlowableTaskResponse {

    private String id;
    private String name;
    private String taskDefinitionKey;
    private String processInstanceId;
    private Long leaveRequestId;
    private String assignee;
    private String state;
    private String approverRole;
    private Instant createTime;
    private Instant claimTime;
    private List<String> candidateUsers;
}
