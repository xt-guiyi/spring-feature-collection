package com.xt.xiaoxingxing.playground.features.postgresql.controller;

import com.xt.xiaoxingxing.playground.features.postgresql.service.PgMyBatisPlusService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 官方 MyBatis-Plus 学习入口。
 *
 * <p>单表场景使用 BaseMapper、Lambda Wrapper 和分页插件；多表场景通过批量查询与
 * Java 组装复现相同结果，用于理解 MyBatis-Plus 核心版不提供 JOIN DSL 的边界。</p>
 */
@RestController
@RequestMapping("/api/playground/pg/mybatis-plus")
public class PgMyBatisPlusController extends AbstractPgDataAccessController {

    public PgMyBatisPlusController(PgMyBatisPlusService service) {
        super(service);
    }
}
