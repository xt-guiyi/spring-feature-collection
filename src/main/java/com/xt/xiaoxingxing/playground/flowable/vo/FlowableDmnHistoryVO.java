package com.xt.xiaoxingxing.playground.flowable.vo;

import lombok.Data;

import java.time.Instant;

/** 一次 DMN 决策执行的历史摘要。 */
@Data
public class FlowableDmnHistoryVO {

    private String id;
    private String decisionKey;
    private String decisionName;
    private String decisionVersion;
    private String decisionDefinitionId;
    private String deploymentId;
    private String instanceId;
    private String executionId;
    private String activityId;
    private String scopeType;
    private boolean failed;
    private Instant startTime;
    private Instant endTime;
    private String executionJson;
}
