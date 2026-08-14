package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 生成分片工作批次的任务参数。 */
@Data
public class GenerateWorkBatchJobParam {

    /** 批次业务键；同一个键重复触发应由数据库唯一约束收敛为同一批工作项。 */
    @NotBlank(message = "batchKey不能为空")
    @Size(max = 200, message = "batchKey长度不能超过200")
    private String batchKey;

    @Min(value = 1, message = "itemCount不能小于1")
    @Max(value = 10000, message = "itemCount不能大于10000")
    private int itemCount;

    /** 大于 0 时，每逢该序号倍数的工作项演示失败；0 表示不注入失败。 */
    @Min(value = 0, message = "failEvery不能小于0")
    private int failEvery;

    @Min(value = 0, message = "failTimes不能小于0")
    @Max(value = 20, message = "failTimes不能大于20")
    private int failTimes;
}
