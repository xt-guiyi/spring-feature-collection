package com.xt.xiaoxingxing.playground.rocketmq.dto;

import com.xt.xiaoxingxing.playground.rocketmq.entity.Product;
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

    private ProductResponse(Long productId,
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

    /** 根据商品生成商品响应。 */
    public static ProductResponse from(Product product, boolean cacheHit) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getPrice(), product.getStock(), cacheHit);
    }
}
