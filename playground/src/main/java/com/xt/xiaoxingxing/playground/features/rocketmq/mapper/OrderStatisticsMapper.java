package com.xt.xiaoxingxing.playground.features.rocketmq.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单统计数据访问接口。 */
@Mapper
public interface OrderStatisticsMapper {

    /** 新增或更新订单统计。 */
    int upsert(@Param("operationType") String operationType,
               @Param("totalAmount") BigDecimal totalAmount,
               @Param("consumedAt") LocalDateTime consumedAt);
}
