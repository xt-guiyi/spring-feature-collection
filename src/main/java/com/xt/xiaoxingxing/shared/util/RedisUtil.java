package com.xt.xiaoxingxing.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 设置字符串值
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串值，并指定过期时间
     *
     * @param key      键
     * @param value    值
     * @param timeout  过期时长
     * @param timeUnit 时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值，不存在时返回 null
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 删除指定的 key
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return true 表示存在
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 获取 key 的剩余过期时间
     *
     * @param key 键
     * @return 剩余秒数，-1 表示永不过期，-2 表示 key 不存在
     */
    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    /**
     * 存储 Java 对象，内部序列化为 JSON 字符串
     *
     * @param key   键
     * @param value 对象值
     */
    public void setObject(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 对象序列化失败", e);
        }
    }

    /**
     * 获取 Java 对象，内部将 JSON 字符串反序列化为对象
     *
     * @param key   键
     * @param clazz 目标对象类型
     * @param <T>   泛型类型
     * @return 反序列化后的对象
     */
    public <T> T getObject(String key, Class<T> clazz) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 对象反序列化失败", e);
        }
    }

    /**
     * 设置 Hash 类型中的某个字段值
     *
     * @param hashKey hash 键
     * @param field   字段名
     * @param value   字段值
     */
    public void setHash(String hashKey, String field, Object value) {
        stringRedisTemplate.opsForHash().put(hashKey, field, value);
    }

    /**
     * 获取 Hash 类型中的某个字段值
     *
     * @param hashKey hash 键
     * @param field   字段名
     * @return 字段值，不存在时返回 null
     */
    public Object getHash(String hashKey, String field) {
        return stringRedisTemplate.opsForHash().get(hashKey, field);
    }
}
