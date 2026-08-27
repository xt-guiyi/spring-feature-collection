package com.xt.xiaoxingxing.playground.elasticsearch.controller;

import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleWriteRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.service.ArticleCrudService;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleDetailVO;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 两套 Elasticsearch 客户端共享的 HTTP CRUD 入口，业务语义由 Service 负责。 */
public abstract class AbstractArticleCrudController {

    private final ArticleCrudService service;

    /** 注入文章 CRUD 服务。 */
    protected AbstractArticleCrudController(ArticleCrudService service) {
        this.service = service;
    }

    /** 创建文章检索投影。 */
    @PostMapping("/articles")
    public Result<ArticleDetailVO> create(@Valid @RequestBody ArticleWriteRequest request) {
        return Result.ok(service.create(request));
    }

    /** 按 ID 查询文章检索投影。 */
    @GetMapping("/articles/{id}")
    public Result<ArticleDetailVO> getById(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /** 更新指定文章检索投影。 */
    @PutMapping("/articles/{id}")
    public Result<ArticleDetailVO> update(@PathVariable String id,
                                          @Valid @RequestBody ArticleWriteRequest request) {
        return Result.ok(service.update(id, request));
    }

    /** 删除指定文章检索投影。 */
    @DeleteMapping("/articles/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        return Result.ok(service.delete(id));
    }
}
