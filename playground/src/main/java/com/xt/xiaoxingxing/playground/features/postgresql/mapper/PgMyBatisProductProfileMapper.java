package com.xt.xiaoxingxing.playground.features.postgresql.mapper;

import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgProductProfile;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileSearchRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 普通 MyBatis JSONB 学习 Mapper。
 *
 * <p>所有 PostgreSQL 原生操作都保留在 XML 中，便于直接观察 {@code ->>}、{@code @>}、
 * {@code #>>}、{@code jsonb_set}、{@code ||} 和 {@code -} 最终组成的 SQL。</p>
 */
@Mapper
public interface PgMyBatisProductProfileMapper {

    Long insertProfile(PgProductProfile profile);

    PgProductProfile selectProfileById(@Param("id") Long id);

    List<PgProductProfile> selectAllProfiles();

    List<PgProductProfile> selectProfilesByCondition(ProductProfileSearchRequest request);

    int mergeAttributes(@Param("id") Long id, @Param("attributes") JsonNode attributes);

    int updateWarrantyMonths(@Param("id") Long id, @Param("months") int months);

    int deleteAttribute(@Param("id") Long id, @Param("key") String key);
}
