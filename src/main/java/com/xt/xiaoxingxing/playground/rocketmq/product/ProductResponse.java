package com.xt.xiaoxingxing.playground.rocketmq.product;

import com.xt.xiaoxingxing.playground.postgresql.entity.PgProduct;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 商品库存查询结果。
 *
 * <p>{@code cacheHit} 只说明本次查询的数据来源，不代表 Redis 是库存事实源。下单是否成功仍由 PostgreSQL
 * 中带库存条件的 UPDATE 裁决，前端不能根据这里的快照自行决定可售数量。</p>
 */
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

    /** 从 Redis 或 PostgreSQL 取得的商品事实构造业务响应。 */
    public static ProductResponse from(PgProduct product, boolean cacheHit) {
        Objects.requireNonNull(product, "商品不能为空");
        return new ProductResponse(
                product.getId(), product.getName(), product.getPrice(), product.getStock(), cacheHit);
    }
}
