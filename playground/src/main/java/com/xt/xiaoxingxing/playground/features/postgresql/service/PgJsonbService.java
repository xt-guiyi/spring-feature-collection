package com.xt.xiaoxingxing.playground.features.postgresql.service;

import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileAttributesMergeRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileCreateRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileSearchRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.response.PgProductProfileResponse;

import java.util.List;

/**
 * PostgreSQL JSONB 学习案例的统一业务契约。
 *
 * <p>普通 MyBatis 与 MyBatis-Plus 各实现一次，使两套 HTTP 入口可以使用完全相同的
 * 参数和返回结构，直接比较 XML 原生 SQL 与官方 Wrapper 的差异。</p>
 */
public interface PgJsonbService {

    Long create(ProductProfileCreateRequest request);

    PgProductProfileResponse getById(Long id);

    List<PgProductProfileResponse> list();

    List<PgProductProfileResponse> search(ProductProfileSearchRequest request);

    PgProductProfileResponse mergeAttributes(Long id, ProductProfileAttributesMergeRequest request);

    PgProductProfileResponse updateWarrantyMonths(Long id, int months);

    PgProductProfileResponse deleteAttribute(Long id, String key);

}
