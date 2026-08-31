package com.xt.xiaoxingxing.playground.flowable.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 候选人领取人工任务的请求。 */
@Data
public class ClaimTaskRequest {

    @NotNull(message = "领取人ID不能为空")
    @Positive(message = "领取人ID必须大于0")
    private Long userId;
}
