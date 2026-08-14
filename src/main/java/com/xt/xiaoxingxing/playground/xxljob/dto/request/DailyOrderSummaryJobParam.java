package com.xt.xiaoxingxing.playground.xxljob.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/** 每日订单汇总任务参数。 */
@Data
public class DailyOrderSummaryJobParam {

    /**
     * 需要汇总的业务日期，可不传。
     *
     * <p>不传时 Handler 会根据本次触发的 logDateTime 推导上海时区的前一天。
     * 注意 XXL-JOB 的失败重试会创建一次新的触发，跨午夜的严格重跑应显式传入 businessDate，
     * 不能把 logDateTime 误认为永远不变的业务幂等键。</p>
     */
    private LocalDate businessDate;

    /** 同一业务日期需要重算时显式递增版本，便于保留可审计的受控重跑语义。 */
    @Min(value = 1, message = "runVersion不能小于1")
    private int runVersion;

    /** 汇总执行权租约，必须大于一次正常汇总的最坏耗时。 */
    @Min(value = 5, message = "leaseSeconds不能小于5")
    @Max(value = 3600, message = "leaseSeconds不能大于3600")
    private int leaseSeconds;
}
