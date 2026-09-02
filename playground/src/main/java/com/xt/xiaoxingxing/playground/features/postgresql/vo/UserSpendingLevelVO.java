package com.xt.xiaoxingxing.playground.features.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

/** PostgreSQL CTE 分阶段统计得到的用户消费等级。 */
@Data
public class UserSpendingLevelVO {

    private Long userId;
    private String username;
    private Long orderCount;
    private BigDecimal totalSpent;
    private String spendingLevel;
}
