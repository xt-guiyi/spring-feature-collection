package com.xt.xiaoxingxing.playground.redis.controller;

import com.xt.xiaoxingxing.playground.redis.dto.request.*;
import com.xt.xiaoxingxing.playground.redis.dto.response.RedisValueResponse;
import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.playground.redis.service.RedisDemoService;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/playground/redis")
@RequiredArgsConstructor
public class RedisDemoController {

    private final RedisDemoService redisDemoService;

    // ==================== String ====================

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

    // ==================== Object ====================

    /**
     * 存储 Java 对象
     */
    @PostMapping("/object")
    public Result<RedisDemo> setObject(@RequestBody RedisDemo demo) {
        return Result.ok(redisDemoService.setObject(demo));
    }

    /**
     * 获取 Java 对象
     */
    @GetMapping("/object")
    public Result<RedisDemo> getObject(@RequestParam String key) {
        return Result.ok(redisDemoService.getObject(key));
    }

    // ==================== Hash ====================

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

    // ==================== List ====================

    /**
     * List 左侧入队
     */
    @PostMapping("/list/left")
    public Result<Long> leftPush(@Valid @RequestBody RedisListRequest request) {
        return Result.ok(redisDemoService.leftPush(request));
    }

    /**
     * List 右侧入队
     */
    @PostMapping("/list/right")
    public Result<Long> rightPush(@Valid @RequestBody RedisListRequest request) {
        return Result.ok(redisDemoService.rightPush(request));
    }

    /**
     * List 左侧出队
     */
    @PostMapping("/list/left-pop")
    public Result<String> leftPop(@RequestParam String key) {
        return Result.ok(redisDemoService.leftPop(key));
    }

    /**
     * 获取 List 范围元素
     */
    @GetMapping("/list")
    public Result<List<String>> listRange(@RequestParam String key,
                                          @RequestParam(defaultValue = "0") long start,
                                          @RequestParam(defaultValue = "-1") long end) {
        return Result.ok(redisDemoService.listRange(key, start, end));
    }

    // ==================== Set ====================

    /**
     * Set 添加元素
     */
    @PostMapping("/set")
    public Result<Long> addSet(@Valid @RequestBody RedisSetAddRequest request) {
        return Result.ok(redisDemoService.addSet(request));
    }

    /**
     * 获取 Set 所有成员
     */
    @GetMapping("/set")
    public Result<Set<String>> members(@RequestParam String key) {
        return Result.ok(redisDemoService.members(key));
    }

    // ==================== ZSet ====================

    /**
     * ZSet 添加元素
     */
    @PostMapping("/zset")
    public Result<Boolean> addZSet(@Valid @RequestBody RedisZSetRequest request) {
        return Result.ok(redisDemoService.addZSet(request));
    }

    /**
     * ZSet 按分数从低到高获取
     */
    @GetMapping("/zset")
    public Result<Set<String>> rangeZSet(@RequestParam String key,
                                         @RequestParam(defaultValue = "0") long start,
                                         @RequestParam(defaultValue = "-1") long end) {
        return Result.ok(redisDemoService.rangeZSet(key, start, end));
    }

    // ==================== Bitmap ====================

    /**
     * Bitmap 设置位值
     */
    @PostMapping("/bitmap")
    public Result<Boolean> setBit(@Valid @RequestBody RedisBitmapRequest request) {
        return Result.ok(redisDemoService.setBit(request));
    }

    /**
     * Bitmap 获取位值
     */
    @GetMapping("/bitmap")
    public Result<Boolean> getBit(@RequestParam String key, @RequestParam long offset) {
        return Result.ok(redisDemoService.getBit(key, offset));
    }

    // ==================== HyperLogLog ====================

    /**
     * HyperLogLog 添加元素
     */
    @PostMapping("/hyperloglog")
    public Result<Long> addHyperLogLog(@Valid @RequestBody RedisHyperLogLogRequest request) {
        return Result.ok(redisDemoService.addHyperLogLog(request));
    }

    /**
     * HyperLogLog 统计基数
     */
    @GetMapping("/hyperloglog")
    public Result<Long> countHyperLogLog(@RequestParam String key) {
        return Result.ok(redisDemoService.countHyperLogLog(key));
    }

    // ==================== Geo ====================

    /**
     * Geo 添加位置
     */
    @PostMapping("/geo")
    public Result<Long> addGeo(@Valid @RequestBody RedisGeoRequest request) {
        return Result.ok(redisDemoService.addGeo(request));
    }

    /**
     * Geo 计算两点距离
     */
    @GetMapping("/geo/distance")
    public Result<Distance> distanceGeo(@RequestParam String key,
                                        @RequestParam String member1,
                                        @RequestParam String member2) {
        return Result.ok(redisDemoService.distanceGeo(key, member1, member2, Metrics.KILOMETERS));
    }

    // ==================== Stream ====================

    /**
     * Stream 添加消息
     */
    @PostMapping("/stream")
    public Result<RecordId> addStream(@Valid @RequestBody RedisStreamRequest request) {
        return Result.ok(redisDemoService.addStream(request));
    }

    /**
     * Stream 读取消息
     */
    @GetMapping("/stream")
    public Result<List<MapRecord<String, Object, Object>>> readStream(@RequestParam String key) {
        return Result.ok(redisDemoService.readStream(key));
    }
}
