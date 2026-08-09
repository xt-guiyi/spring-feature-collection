package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import org.apache.ibatis.annotations.Mapper;

/** order_products 表的 MyBatis-Plus 单表操作入口。 */
@Mapper
public interface PgOrderProductPlusMapper extends BaseMapper<PgOrderProduct> {
}
