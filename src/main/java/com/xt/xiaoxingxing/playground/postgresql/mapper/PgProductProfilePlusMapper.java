package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProductProfile;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 商品 JSONB 扩展信息入口；复杂运算由官方 Wrapper 安全组织 SQL。 */
@Mapper
public interface PgProductProfilePlusMapper extends BaseMapper<PgProductProfile> {
}
