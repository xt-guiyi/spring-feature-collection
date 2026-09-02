package com.xt.xiaoxingxing.playground.features.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/** 每日订单汇总任务参数。 */
@Data
public class DailyOrderSummaryJobParam {

    /** 业务日期；为空时使用触发时间对应业务时区的前一天。 */
    private LocalDate businessDate;

    /** 重算版本。 */
    @Min(value = 1, message = "runVersion不能小于1")
    private int runVersion;

    /** 执行租约秒数。 */
    @Min(value = 5, message = "leaseSeconds不能小于5")
    @Max(value = 3600, message = "leaseSeconds不能大于3600")
    private int leaseSeconds;
}
