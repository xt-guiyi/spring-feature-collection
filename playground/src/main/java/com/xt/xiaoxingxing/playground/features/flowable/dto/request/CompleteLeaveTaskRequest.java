package com.xt.xiaoxingxing.playground.features.flowable.dto.request;

import com.xt.xiaoxingxing.playground.features.flowable.enums.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 完成人工审批任务的请求；审批人必须先通过 claim 接口取得任务。 */
@Data
public class CompleteLeaveTaskRequest {

    @NotNull(message = "审批人ID不能为空")
    @Positive(message = "审批人ID必须大于0")
    private Long userId;

    @NotNull(message = "审批决定不能为空")
    private ApprovalDecision decision;

    @Size(max = 1000, message = "审批意见不能超过1000个字符")
    private String comment;
}
