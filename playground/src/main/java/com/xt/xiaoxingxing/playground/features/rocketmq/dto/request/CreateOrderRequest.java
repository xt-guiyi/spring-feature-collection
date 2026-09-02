package com.xt.xiaoxingxing.playground.features.rocketmq.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建订单请求。 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;

    @NotBlank(message = "订单号不能为空")
    @Size(max = 50, message = "订单号长度不能超过50")
    private String orderNo;

    @NotEmpty(message = "订单商品不能为空")
    private List<@NotNull(message = "订单商品不能包含空元素") @Valid CreateOrderItemRequest> items;
}
