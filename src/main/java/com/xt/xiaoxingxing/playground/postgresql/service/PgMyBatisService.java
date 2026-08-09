package com.xt.xiaoxingxing.playground.postgresql.service;

/**
 * 普通 MyBatis 业务入口标记接口。
 *
 * <p>继承统一契约但不新增方法，使 Controller 能通过类型明确选择 XML SQL 实现。</p>
 */
public interface PgMyBatisService extends PgDataAccessService {
}
