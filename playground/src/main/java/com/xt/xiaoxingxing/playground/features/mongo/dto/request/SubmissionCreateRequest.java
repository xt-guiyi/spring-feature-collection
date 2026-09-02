package com.xt.xiaoxingxing.playground.features.mongo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/** 一次提交包含一个 playground 本地用户 ID 和当前问卷的答案列表。 */
@Data
public class SubmissionCreateRequest {

    @NotNull(message = "答题用户不能为空")
    @Positive(message = "答题用户ID必须大于0")
    private Long userId;

    @Valid
    @NotNull(message = "答案列表不能为空")
    private List<AnswerRequest> answers;
}
