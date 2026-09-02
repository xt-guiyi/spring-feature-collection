package com.xt.xiaoxingxing.playground.features.redis.repository;

import com.xt.xiaoxingxing.playground.features.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.shared.infrastructure.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metric;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisDemoRepository {

    private final RedisUtil redisUtil;

    // String
    public void set(String key, String value) {
        redisUtil.set(key, value);
    }

    public void set(String key, String value, long timeout, TimeUnit timeUnit) {
        redisUtil.set(key, value, timeout, timeUnit);
    }

    public String get(String key) {
        return redisUtil.get(key);
    }

    public Boolean delete(String key) {
        return redisUtil.delete(key);
    }

    public Long getExpire(String key) {
        return redisUtil.getExpire(key);
    }

    // Object
    public void setObject(String key, RedisDemo demo) {
        redisUtil.setObject(key, demo);
    }

    public RedisDemo getObject(String key) {
        return redisUtil.getObject(key, RedisDemo.class);
    }

    // Hash
    public void setHash(String hashKey, String field, Object value) {
        redisUtil.setHash(hashKey, field, value);
    }

    public Object getHash(String hashKey, String field) {
        return redisUtil.getHash(hashKey, field);
    }

    // List
    public Long leftPush(String key, String value) {
        return redisUtil.leftPush(key, value);
    }

    public Long rightPush(String key, String value) {
        return redisUtil.rightPush(key, value);
    }

    public String leftPop(String key) {
        return redisUtil.leftPop(key);
    }

    public List<String> listRange(String key, long start, long end) {
        return redisUtil.listRange(key, start, end);
    }

    // Set
    public Long addSet(String key, String value) {
        return redisUtil.addSet(key, value);
    }

    public Set<String> members(String key) {
        return redisUtil.members(key);
    }

    // ZSet
    public Boolean addZSet(String key, String value, double score) {
        return redisUtil.addZSet(key, value, score);
    }

    public Set<String> rangeZSet(String key, long start, long end) {
        return redisUtil.rangeZSet(key, start, end);
    }

    // Bitmap
    public Boolean setBit(String key, long offset, boolean value) {
        return redisUtil.setBit(key, offset, value);
    }

    public Boolean getBit(String key, long offset) {
        return redisUtil.getBit(key, offset);
    }

    // HyperLogLog
    public Long addHyperLogLog(String key, String value) {
        return redisUtil.addHyperLogLog(key, value);
    }

    public Long countHyperLogLog(String key) {
        return redisUtil.countHyperLogLog(key);
    }

    // Geo
    public Long addGeo(String key, double longitude, double latitude, String member) {
        return redisUtil.addGeo(key, longitude, latitude, member);
    }

    public Distance distanceGeo(String key, String member1, String member2, Metric metric) {
        return redisUtil.distanceGeo(key, member1, member2, metric);
    }

    // Stream
    public RecordId addStream(String key, java.util.Map<String, String> fields) {
        return redisUtil.addStream(key, fields);
    }

    public List<MapRecord<String, Object, Object>> readStream(String key) {
        return redisUtil.readStream(key);
    }
}
