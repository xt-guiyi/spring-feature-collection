package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningOrderSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 从订单事实聚合并以版本条件保存日报。 */
@Mapper
public interface XxlLearningOrderSummaryMapper {
    XxlLearningOrderSummary aggregateOrders(@Param("sourceStartAt") LocalDateTime sourceStartAt,
                                             @Param("sourceEndAt") LocalDateTime sourceEndAt);

    int upsertHigherVersion(XxlLearningOrderSummary summary);

    XxlLearningOrderSummary selectByDate(@Param("summaryDate") LocalDate summaryDate);

    List<XxlLearningOrderSummary> selectPage(@Param("dateFrom") LocalDate dateFrom,
                                              @Param("dateTo") LocalDate dateTo,
                                              @Param("offset") long offset,
                                              @Param("pageSize") int pageSize);

    long countPage(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
