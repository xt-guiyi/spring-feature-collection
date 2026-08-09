package com.xt.xiaoxingxing.playground.postgresql.controller;

import com.xt.xiaoxingxing.playground.postgresql.service.PgJsonbService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 普通 MyBatis JSONB 入口：核心 SQL 位于 PgMyBatisProductProfileMapper.xml。 */
@RestController
@RequestMapping("/api/playground/pg/mybatis/jsonb")
public class PgMyBatisJsonbController extends AbstractPgJsonbController {

    public PgMyBatisJsonbController(
            @Qualifier("pgMyBatisJsonbServiceImpl") PgJsonbService service) {
        super(service);
    }
}
