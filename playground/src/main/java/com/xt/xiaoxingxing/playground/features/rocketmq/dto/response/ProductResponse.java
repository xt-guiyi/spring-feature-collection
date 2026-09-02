package com.xt.xiaoxingxing.playground.features.rocketmq.dto.response;

import lombok.Getter;

import java.math.BigDecimal;

/** 商品响应。 */
@Getter
public final class ProductResponse {

    private final Long productId;
    private final String name;
    private final BigDecimal price;
    private final Integer stock;
    private final Boolean cacheHit;

    public ProductResponse(Long productId,
                           String name,
                           BigDecimal price,
                           Integer stock,
                           Boolean cacheHit) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.cacheHit = cacheHit;
    }

}
