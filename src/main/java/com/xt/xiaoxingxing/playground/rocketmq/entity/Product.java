package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.math.BigDecimal;

/** 商品。 */
@Data
public class Product {

    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;
}
