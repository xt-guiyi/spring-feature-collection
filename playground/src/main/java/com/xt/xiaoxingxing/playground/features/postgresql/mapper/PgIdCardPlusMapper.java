package com.xt.xiaoxingxing.playground.features.postgresql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgIdCard;
import org.apache.ibatis.annotations.Mapper;

/** id_cards 表的 MyBatis-Plus 单表操作入口。 */
@Mapper
public interface PgIdCardPlusMapper extends BaseMapper<PgIdCard> {
}
