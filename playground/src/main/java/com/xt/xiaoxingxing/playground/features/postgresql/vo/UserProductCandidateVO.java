package com.xt.xiaoxingxing.playground.features.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 用户与商品 CROSS JOIN 产生的候选组合。 */
@Data
public class UserProductCandidateVO {

    private Long userId;
    private String username;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
}
