package com.xt.xiaoxingxing.playground.flowable.dto.request;

import com.xt.xiaoxingxing.playground.flowable.enums.LeaveType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 直接执行 DMN 路由决策的输入。 */
@Data
public class LeaveRouteEvaluateRequest {

    @NotNull(message = "请假天数不能为空")
    @Min(value = 1, message = "请假天数不能小于1天")
    @Max(value = 30, message = "请假天数不能超过30天")
    private Integer leaveDays;

    /** 可选；DMN 第一版只按 leaveDays 路由，省略时仅在响应中按 ANNUAL 展示。 */
    private LeaveType leaveType;
}
