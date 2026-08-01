package com.xt.xiaoxingxing.playground.redis.repository;

import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.shared.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisDemoRepository {

    private final RedisUtil redisUtil;

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

    public void setObject(String key, RedisDemo demo) {
        redisUtil.setObject(key, demo);
    }

    public RedisDemo getObject(String key) {
        return redisUtil.getObject(key, RedisDemo.class);
    }

    public void setHash(String hashKey, String field, Object value) {
        redisUtil.setHash(hashKey, field, value);
    }

    public Object getHash(String hashKey, String field) {
        return redisUtil.getHash(hashKey, field);
    }
}
