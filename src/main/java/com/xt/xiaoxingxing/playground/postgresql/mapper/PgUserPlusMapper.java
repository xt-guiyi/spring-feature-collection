package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * users 表的 MyBatis-Plus Mapper。
 *
 * <p>不声明自定义 SQL，用于展示 BaseMapper 官方提供的单表 CRUD 能力。</p>
 */
@Mapper
public interface PgUserPlusMapper extends BaseMapper<PgUser> {
}
