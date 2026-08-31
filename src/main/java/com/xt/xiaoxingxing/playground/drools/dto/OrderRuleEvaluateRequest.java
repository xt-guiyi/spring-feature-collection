package com.xt.xiaoxingxing.playground.drools.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 订单规则计算请求。 */
@Data
public class OrderRuleEvaluateRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于0")
    @Digits(integer = 12, fraction = 2, message = "订单金额最多12位整数和2位小数")
    private BigDecimal totalAmount;

    private boolean vip;

    private boolean newUser;
}
