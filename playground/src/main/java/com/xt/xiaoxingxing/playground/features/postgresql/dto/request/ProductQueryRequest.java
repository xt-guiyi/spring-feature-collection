package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/** 商品名称、价格和库存动态查询条件。 */
@Data
public class ProductQueryRequest {

    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minStock;
}
