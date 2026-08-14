package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 分片领取并处理工作项的任务参数。 */
@Data
public class ProcessWorkItemsJobParam {

    @NotBlank(message = "batchKey不能为空")
    @Size(max = 200, message = "batchKey长度不能超过200")
    private String batchKey;

    /** 单个分片每轮最多领取的工作项数量，限制批量可避免一次任务无限占用执行线程。 */
    @Min(value = 1, message = "batchSize不能小于1")
    @Max(value = 500, message = "batchSize不能大于500")
    private int batchSize;

    /** 单个工作项允许的最大业务尝试次数，耗尽后应进入持久化终态而非永久重试。 */
    @Min(value = 1, message = "maxAttempts不能小于1")
    @Max(value = 50, message = "maxAttempts不能大于50")
    private int maxAttempts;

    /** 工作项领取租约，必须覆盖单批正常处理的最坏耗时。 */
    @Min(value = 5, message = "leaseSeconds不能小于5")
    @Max(value = 3600, message = "leaseSeconds不能大于3600")
    private int leaseSeconds;

    /** 失败工作项再次可领取前的退避时间，0 表示立即具备重试资格。 */
    @Min(value = 0, message = "retryDelaySeconds不能小于0")
    @Max(value = 3600, message = "retryDelaySeconds不能大于3600")
    private int retryDelaySeconds;
}
