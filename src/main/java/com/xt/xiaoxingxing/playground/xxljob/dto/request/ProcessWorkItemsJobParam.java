package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 分片领取并处理工作项的任务参数。 */
@Data
public class ProcessWorkItemsJobParam {

    /** 每个分片单轮领取数量。 */
    @Min(value = 1, message = "batchSize不能小于1")
    @Max(value = 500, message = "batchSize不能大于500")
    private int batchSize;

    /** 工作项最大尝试次数。 */
    @Min(value = 1, message = "maxAttempts不能小于1")
    @Max(value = 50, message = "maxAttempts不能大于50")
    private int maxAttempts;

    /** 工作项租约秒数。 */
    @Min(value = 5, message = "leaseSeconds不能小于5")
    @Max(value = 3600, message = "leaseSeconds不能大于3600")
    private int leaseSeconds;

    /** 失败重试间隔秒数。 */
    @Min(value = 0, message = "retryDelaySeconds不能小于0")
    @Max(value = 3600, message = "retryDelaySeconds不能大于3600")
    private int retryDelaySeconds;
}
