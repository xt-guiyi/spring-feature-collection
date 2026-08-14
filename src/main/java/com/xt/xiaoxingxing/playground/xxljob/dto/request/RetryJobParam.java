package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 失败重试学习任务参数。 */
@Data
public class RetryJobParam {

    /** 稳定业务键用于跨不同调度日志识别同一次业务动作，不能用随机 UUID 替代。 */
    @NotBlank(message = "businessKey不能为空")
    @Size(max = 200, message = "businessKey长度不能超过200")
    private String businessKey;

    /** 前多少次业务尝试主动失败，用于观察 XXL-JOB 创建的新重试调度。 */
    @Min(value = 0, message = "failTimes不能小于0")
    @Max(value = 20, message = "failTimes不能大于20")
    private int failTimes;

    /** 执行权租约，防止进程崩溃后记录永久占用，同时必须覆盖正常处理的最坏耗时。 */
    @Min(value = 5, message = "leaseSeconds不能小于5")
    @Max(value = 3600, message = "leaseSeconds不能大于3600")
    private int leaseSeconds;
}
