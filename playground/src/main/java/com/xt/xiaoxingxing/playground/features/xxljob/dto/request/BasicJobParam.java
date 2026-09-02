package com.xt.xiaoxingxing.playground.features.xxljob.dto.request;

import com.xt.xiaoxingxing.playground.features.xxljob.enums.BasicJobOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 基础任务参数。 */
@Data
public class BasicJobParam {

    @NotBlank(message = "message不能为空")
    private String message;

    /** 任务结果，默认成功。 */
    @NotNull(message = "outcome不能为空")
    private BasicJobOutcome outcome = BasicJobOutcome.SUCCESS;
}
