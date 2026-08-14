package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 基础任务参数，用一套 Handler 对比成功、显式失败和异常失败。 */
@Data
public class BasicJobParam {

    @NotBlank(message = "message不能为空")
    private String message;

    /** 未传时默认成功，便于先完成最小 Hello World，再逐步观察失败路径。 */
    @NotNull(message = "outcome不能为空")
    private BasicJobOutcome outcome = BasicJobOutcome.SUCCESS;
}
