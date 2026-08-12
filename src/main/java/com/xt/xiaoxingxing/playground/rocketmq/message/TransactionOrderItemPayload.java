package com.xt.xiaoxingxing.playground.rocketmq.message;

import lombok.Data;

/** 事务订单命令中的商品和购买数量，只保留本地订单事务真正需要的输入。 */
@Data
public class TransactionOrderItemPayload {

    private Long productId;
    private Integer quantity;
}
