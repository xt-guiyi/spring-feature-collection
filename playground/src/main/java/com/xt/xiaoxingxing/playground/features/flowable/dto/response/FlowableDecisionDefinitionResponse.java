package com.xt.xiaoxingxing.playground.features.flowable.dto.response;

import lombok.Data;

/** DMN 决策定义摘要。 */
@Data
public class FlowableDecisionDefinitionResponse {

    private String id;
    private String key;
    private String name;
    private Integer version;
    private String resourceName;
    private String deploymentId;
    private String category;
    private String tenantId;
    private String decisionType;
}
