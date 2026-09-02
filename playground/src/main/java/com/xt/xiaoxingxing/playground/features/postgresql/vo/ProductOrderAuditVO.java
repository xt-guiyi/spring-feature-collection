package com.xt.xiaoxingxing.playground.features.postgresql.vo;

import lombok.Data;

/**
 * 商品与订单明细 FULL OUTER JOIN 审计结果。
 *
 * <p>FULL OUTER JOIN 会保留两边所有数据：未售商品没有 orderProductId，
 * 无效关联没有 productName。matchStatus 用于解释当前行的匹配状态。</p>
 */
@Data
public class ProductOrderAuditVO {

    private Long productId;
    private String productName;
    private Long orderProductId;
    private Long orderId;
    private Integer quantity;
    private String matchStatus;
}
