package com.xt.xiaoxingxing.playground.features.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 商品销量和销售额聚合统计。 */
@Data
public class ProductSalesStatVO {

    private Long productId;
    private String productName;
    private Long totalQuantity;
    private BigDecimal totalSales;
    private Integer stock;
}
