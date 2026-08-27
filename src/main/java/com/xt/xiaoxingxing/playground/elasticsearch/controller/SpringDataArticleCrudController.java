package com.xt.xiaoxingxing.playground.elasticsearch.controller;

import com.xt.xiaoxingxing.playground.elasticsearch.service.impl.SpringDataArticleCrudService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Spring Data Elasticsearch 的文章 CRUD 入口。 */
@RestController
@Validated
@RequestMapping("/api/playground/elasticsearch/spring-data")
public class SpringDataArticleCrudController extends AbstractArticleCrudController {

    /** 注入 Spring Data Elasticsearch 的文章 CRUD 服务。 */
    public SpringDataArticleCrudController(SpringDataArticleCrudService service) {
        super(service);
    }
}
