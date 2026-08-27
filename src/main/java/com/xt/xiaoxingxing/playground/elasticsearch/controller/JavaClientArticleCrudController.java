package com.xt.xiaoxingxing.playground.elasticsearch.controller;

import com.xt.xiaoxingxing.playground.elasticsearch.service.impl.JavaClientArticleCrudService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Elasticsearch Java API Client 的文章 CRUD 入口。 */
@RestController
@Validated
@RequestMapping("/api/playground/elasticsearch/java-client")
public class JavaClientArticleCrudController extends AbstractArticleCrudController {

    /** 注入 Java API Client 的文章 CRUD 服务。 */
    public JavaClientArticleCrudController(JavaClientArticleCrudService service) {
        super(service);
    }
}
