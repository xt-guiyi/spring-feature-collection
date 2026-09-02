package com.xt.xiaoxingxing.playground.features.postgresql.controller;

import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileAttributesMergeRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileCreateRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileSearchRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.response.PgProductProfileResponse;
import com.xt.xiaoxingxing.playground.features.postgresql.service.PgJsonbService;
import com.xt.xiaoxingxing.shared.core.response.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 两套 PostgreSQL JSONB 学习入口共享的端点定义。
 *
 * <p>Controller 只负责 HTTP 参数转换；JSON 对象校验、商品存在校验、动态 SQL 和影响行数
 * 判断全部放在对应 Service 中，保证 MyBatis 与 MyBatis-Plus 的接口语义一致。</p>
 */
public abstract class AbstractPgJsonbController {

    private final PgJsonbService service;

    protected AbstractPgJsonbController(PgJsonbService service) {
        this.service = service;
    }

    /** 创建商品扩展信息；attributes 可以包含字符串、数字、布尔值、数组和嵌套对象。 */
    @PostMapping("/product-profiles")
    public Result<Long> create(@RequestBody ProductProfileCreateRequest request) {
        return Result.ok(service.create(request));
    }

    /** 查询一条记录；JsonNode 会被 Jackson 直接序列化为普通 JSON 对象返回前端。 */
    @GetMapping("/product-profiles/{id}")
    public Result<PgProductProfileResponse> getById(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    /** 查询全部商品扩展记录并按主键升序返回。 */
    @GetMapping("/product-profiles")
    public Result<List<PgProductProfileResponse>> list() {
        return Result.ok(service.list());
    }

    /** 动态演示 ->>、@>、键存在和 #>> 四类 JSONB 查询。 */
    @PostMapping("/product-profiles/search")
    public Result<List<PgProductProfileResponse>> search(
            @RequestBody(required = false) ProductProfileSearchRequest request) {
        return Result.ok(service.search(request));
    }

    /** 使用 JSONB || 合并顶层对象，同名属性覆盖，未出现的属性保持不变。 */
    @PutMapping("/product-profiles/{id}/attributes")
    public Result<PgProductProfileResponse> mergeAttributes(
            @PathVariable Long id,
            @RequestBody ProductProfileAttributesMergeRequest request) {
        return Result.ok(service.mergeAttributes(id, request));
    }

    /** 使用 jsonb_set 局部更新 warranty.months，不覆盖 warranty.enabled。 */
    @PutMapping("/product-profiles/{id}/warranty-months")
    public Result<PgProductProfileResponse> updateWarrantyMonths(
            @PathVariable Long id,
            @RequestParam int months) {
        return Result.ok(service.updateWarrantyMonths(id, months));
    }

    /** 使用 JSONB - text 删除一个顶层属性，不会删除整条 product_profiles 记录。 */
    @DeleteMapping("/product-profiles/{id}/attributes/{key}")
    public Result<PgProductProfileResponse> deleteAttribute(
            @PathVariable Long id,
            @PathVariable String key) {
        return Result.ok(service.deleteAttribute(id, key));
    }
}
