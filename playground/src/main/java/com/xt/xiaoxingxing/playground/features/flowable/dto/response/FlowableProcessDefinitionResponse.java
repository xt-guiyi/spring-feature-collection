package com.xt.xiaoxingxing.playground.features.flowable.dto.response;

import lombok.Data;

/** BPMN 流程定义摘要。 */
@Data
public class FlowableProcessDefinitionResponse {

    private String id;
    private String key;
    private String name;
    private Integer version;
    private String resourceName;
    private String deploymentId;
    private String category;
    private String tenantId;
    private Boolean suspended;
}
