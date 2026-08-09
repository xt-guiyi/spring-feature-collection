package com.xt.xiaoxingxing.playground.postgresql.controller;

import com.xt.xiaoxingxing.playground.postgresql.service.PgJsonbService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MyBatis-Plus JSONB 入口：BaseMapper 负责 CRUD，Wrapper 组织 PostgreSQL JSONB SQL。 */
@RestController
@RequestMapping("/api/playground/pg/mybatis-plus/jsonb")
public class PgMyBatisPlusJsonbController extends AbstractPgJsonbController {

    public PgMyBatisPlusJsonbController(
            @Qualifier("pgMyBatisPlusJsonbServiceImpl") PgJsonbService service) {
        super(service);
    }
}
