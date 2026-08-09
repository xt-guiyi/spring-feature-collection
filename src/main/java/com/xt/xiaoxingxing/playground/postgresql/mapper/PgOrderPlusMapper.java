package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import org.apache.ibatis.annotations.Mapper;

/** orders 表的 MyBatis-Plus 单表操作入口。 */
@Mapper
public interface PgOrderPlusMapper extends BaseMapper<PgOrder> {
}
