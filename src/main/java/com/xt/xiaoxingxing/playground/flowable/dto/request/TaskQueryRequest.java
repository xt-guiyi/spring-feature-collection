package com.xt.xiaoxingxing.playground.flowable.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工任务分页查询条件。candidateUser 与 assignee 二选一，也可以按流程实例筛选。 */
@Data
public class TaskQueryRequest {

    @Size(max = 80, message = "候选人ID长度不能超过80")
    private String candidateUser;

    @Size(max = 80, message = "办理人ID长度不能超过80")
    private String assignee;

    @Size(max = 80, message = "流程实例ID长度不能超过80")
    private String processInstanceId;

    @Size(max = 80, message = "任务定义Key长度不能超过80")
    private String taskDefinitionKey;

    @Min(value = 1, message = "pageNum必须大于0")
    private int pageNum = 1;

    @Min(value = 1, message = "pageSize必须大于0")
    @Max(value = 100, message = "pageSize不能超过100")
    private int pageSize = 10;
}
