package com.xt.xiaoxingxing.playground.features.postgresql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgOrderStatusEnumDemo;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus 订单状态枚举自动映射入口。
 *
 * <p>它与 {@link PgOrderPlusMapper} 都读取 orders 表，但泛型实体的 status 字段类型不同：
 * 原 Mapper 返回 String，本 Mapper 返回枚举。通过 BaseMapper 的标准 selectList 即可触发
 * MyBatis-Plus 枚举类型处理器，不需要手写状态码转换 SQL 或 Java switch。</p>
 */
@Mapper
public interface PgOrderStatusEnumPlusMapper extends BaseMapper<PgOrderStatusEnumDemo> {
}
