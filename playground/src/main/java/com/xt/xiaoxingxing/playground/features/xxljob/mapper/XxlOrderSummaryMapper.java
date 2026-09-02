package com.xt.xiaoxingxing.playground.features.xxljob.mapper;

import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlOrderSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 订单日报数据访问。 */
@Mapper
public interface XxlOrderSummaryMapper {
    XxlOrderSummary aggregateOrders(@Param("sourceStartAt") LocalDateTime sourceStartAt,
                                             @Param("sourceEndAt") LocalDateTime sourceEndAt);

    int upsertHigherVersion(XxlOrderSummary summary);

    XxlOrderSummary selectByDate(@Param("summaryDate") LocalDate summaryDate);

    List<XxlOrderSummary> selectPage(@Param("dateFrom") LocalDate dateFrom,
                                              @Param("dateTo") LocalDate dateTo,
                                              @Param("offset") long offset,
                                              @Param("pageSize") int pageSize);

    long countPage(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
