package com.xt.xiaoxingxing.playground.redis.controller;

import com.xt.xiaoxingxing.shared.common.Result;
import com.xt.xiaoxingxing.playground.redis.dto.request.RedisHashSetRequest;
import com.xt.xiaoxingxing.playground.redis.dto.request.RedisSetRequest;
import com.xt.xiaoxingxing.playground.redis.dto.response.RedisValueResponse;
import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.playground.redis.service.RedisDemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/playground/redis")
@RequiredArgsConstructor
public class RedisDemoController {

    private final RedisDemoService redisDemoService;

    /**
     * 设置字符串值
     */
    @PostMapping("/string")
    public Result<RedisValueResponse> set(@Valid @RequestBody RedisSetRequest request) {
        return Result.ok(redisDemoService.set(request));
    }

    /**
     * 获取字符串值
     */
    @GetMapping("/string")
    public Result<RedisValueResponse> get(@RequestParam String key) {
        return Result.ok(redisDemoService.get(key));
    }

    /**
     * 删除 key
     */
    @DeleteMapping("/string")
    public Result<Boolean> delete(@RequestParam String key) {
        return Result.ok(redisDemoService.delete(key));
    }

    /**
     * 存储对象
     */
    @PostMapping("/object")
    public Result<RedisDemo> setObject(@RequestBody RedisDemo demo) {
        return Result.ok(redisDemoService.setObject(demo));
    }

    /**
     * 获取对象
     */
    @GetMapping("/object")
    public Result<RedisDemo> getObject(@RequestParam String key) {
        return Result.ok(redisDemoService.getObject(key));
    }

    /**
     * 设置 hash 字段
     */
    @PostMapping("/hash")
    public Result<String> setHash(@Valid @RequestBody RedisHashSetRequest request) {
        return Result.ok(redisDemoService.setHash(request));
    }

    /**
     * 获取 hash 字段
     */
    @GetMapping("/hash")
    public Result<Object> getHash(@RequestParam String hashKey, @RequestParam String field) {
        return Result.ok(redisDemoService.getHash(hashKey, field));
    }
}
