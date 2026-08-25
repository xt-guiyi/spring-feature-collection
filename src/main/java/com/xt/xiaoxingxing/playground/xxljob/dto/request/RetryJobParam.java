package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 失败重试学习任务参数。 */
@Data
public class RetryJobParam {

    /** 业务幂等键。 */
    @NotBlank(message = "businessKey不能为空")
    @Size(max = 200, message = "businessKey长度不能超过200")
    private String businessKey;

    /** 计划失败次数。 */
    @Min(value = 0, message = "failTimes不能小于0")
    @Max(value = 20, message = "failTimes不能大于20")
    private int failTimes;

    /** 执行租约秒数。 */
    @Min(value = 5, message = "leaseSeconds不能小于5")
    @Max(value = 3600, message = "leaseSeconds不能大于3600")
    private int leaseSeconds;
}
