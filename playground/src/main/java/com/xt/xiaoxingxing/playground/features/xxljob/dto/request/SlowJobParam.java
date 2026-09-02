package com.xt.xiaoxingxing.playground.features.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 慢任务参数，用来观察阻塞策略、超时和线程中断。 */
@Data
public class SlowJobParam {

    @Min(value = 1, message = "seconds不能小于1")
    @Max(value = 3600, message = "seconds不能大于3600")
    private int seconds;
}
