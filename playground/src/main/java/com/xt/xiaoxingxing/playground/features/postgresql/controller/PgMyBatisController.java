package com.xt.xiaoxingxing.playground.features.postgresql.controller;

import com.xt.xiaoxingxing.playground.features.postgresql.service.PgMyBatisService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通 MyBatis 学习入口。
 *
 * <p>本入口的复杂场景由 Mapper XML 中的数据库原生 SQL 完成，适合学习动态标签、
 * JOIN、子查询、聚合、窗口函数和 PostgreSQL 特有语法。</p>
 */
@RestController
@RequestMapping("/api/playground/pg/mybatis")
public class PgMyBatisController extends AbstractPgDataAccessController {

    public PgMyBatisController(PgMyBatisService service) {
        super(service);
    }
}
