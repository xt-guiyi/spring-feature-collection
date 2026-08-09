package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 创建完整订单请求。
 *
 * <p>客户端只提交商品和数量，单价及订单总金额必须以数据库中的商品价格计算，
 * 避免信任客户端传入的金额。</p>
 */
@Data
public class CompleteOrderCreateRequest {

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @Valid
    @NotEmpty(message = "订单商品不能为空")
    private List<CompleteOrderItemRequest> items;
}
