package com.xt.xiaoxingxing.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metric;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 从 List 左侧插入一个元素
     *
     * @param key   键
     * @param value 元素值
     * @return 列表长度
     */
    public Long leftPush(String key, String value) {
        return stringRedisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 从 List 右侧插入一个元素
     *
     * @param key   键
     * @param value 元素值
     * @return 列表长度
     */
    public Long rightPush(String key, String value) {
        return stringRedisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 从 List 左侧弹出一个元素
     *
     * @param key 键
     * @return 弹出的元素，不存在时返回 null
     */
    public String leftPop(String key) {
        return stringRedisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从 List 右侧弹出一个元素
     *
     * @param key 键
     * @return 弹出的元素，不存在时返回 null
     */
    public String rightPop(String key) {
        return stringRedisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取 List 指定范围的元素
     *
     * @param key   键
     * @param start 起始索引，0 表示第一个
     * @param end   结束索引，-1 表示最后一个
     * @return 元素列表
     */
    public List<String> listRange(String key, long start, long end) {
        return stringRedisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 向 Set 中添加元素
     *
     * @param key    键
     * @param values 元素值，可多个
     * @return 新增成功的元素个数
     */
    public Long addSet(String key, String... values) {
        return stringRedisTemplate.opsForSet().add(key, values);
    }

    /**
     * 判断元素是否在 Set 中
     *
     * @param key   键
     * @param value 元素值
     * @return true 表示存在
     */
    public Boolean isMember(String key, String value) {
        return stringRedisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * 获取 Set 中的所有成员
     *
     * @param key 键
     * @return 成员集合
     */
    public Set<String> members(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }

    /**
     * 从 Set 中移除元素
     *
     * @param key    键
     * @param values 要移除的元素
     * @return 移除成功的个数
     */
    public Long removeSet(String key, String... values) {
        return stringRedisTemplate.opsForSet().remove(key, (Object[]) values);
    }

    /**
     * 向 ZSet 中添加元素及其分数
     *
     * @param key   键
     * @param value 元素值
     * @param score 分数，用于排序
     * @return true 表示新增成功
     */
    public Boolean addZSet(String key, String value, double score) {
        return stringRedisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * 获取 ZSet 按分数从低到高排序后的元素
     *
     * @param key   键
     * @param start 起始索引
     * @param end   结束索引
     * @return 元素集合
     */
    public Set<String> rangeZSet(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * 获取 ZSet 按分数从高到低排序后的元素
     *
     * @param key   键
     * @param start 起始索引
     * @param end   结束索引
     * @return 元素集合
     */
    public Set<String> reverseRangeZSet(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * 从 ZSet 中移除元素
     *
     * @param key    键
     * @param values 要移除的元素
     * @return 移除成功的个数
     */
    public Long removeZSet(String key, String... values) {
        return stringRedisTemplate.opsForZSet().remove(key, (Object[]) values);
    }

    /**
     * 设置 Bitmap 指定偏移位置的位值
     *
     * @param key    键
     * @param offset 偏移位置
     * @param value  true 表示 1，false 表示 0
     * @return 该位置原来的值
     */
    public Boolean setBit(String key, long offset, boolean value) {
        return stringRedisTemplate.opsForValue().setBit(key, offset, value);
    }

    /**
     * 获取 Bitmap 指定偏移位置的位值
     *
     * @param key    键
     * @param offset 偏移位置
     * @return true 表示 1，false 表示 0
     */
    public Boolean getBit(String key, long offset) {
        return stringRedisTemplate.opsForValue().getBit(key, offset);
    }

    /**
     * 统计 Bitmap 中值为 1 的位数
     *
     * @param key 键
     * @return 1 的个数
     */
    public Long bitCount(String key) {
        return stringRedisTemplate.execute((RedisCallback<Long>) connection -> connection.stringCommands().bitCount(key.getBytes()));
    }

    /**
     * 向 HyperLogLog 中添加元素
     *
     * @param key    键
     * @param values 元素值，可多个
     * @return 1 表示内部寄存器有变化
     */
    public Long addHyperLogLog(String key, String... values) {
        return stringRedisTemplate.opsForHyperLogLog().add(key, values);
    }

    /**
     * 统计 HyperLogLog 中的不重复元素个数（近似值）
     *
     * @param keys 键，可多个
     * @return 近似基数
     */
    public Long countHyperLogLog(String... keys) {
        return stringRedisTemplate.opsForHyperLogLog().size(keys);
    }

    /**
     * 添加地理位置
     *
     * @param key       键
     * @param longitude 经度
     * @param latitude  纬度
     * @param member    成员名称
     * @return 新增的个数
     */
    public Long addGeo(String key, double longitude, double latitude, String member) {
        return stringRedisTemplate.opsForGeo().add(key, new Point(longitude, latitude), member);
    }

    /**
     * 计算两个地理位置之间的距离
     *
     * @param key    键
     * @param member1 成员 1
     * @param member2 成员 2
     * @param metric 距离单位，如 Metrics.KILOMETERS
     * @return 距离
     */
    public Distance distanceGeo(String key, String member1, String member2, Metric metric) {
        return stringRedisTemplate.opsForGeo().distance(key, member1, member2, metric);
    }

    /**
     * 向 Stream 中添加一条消息
     *
     * @param key    键
     * @param fields 消息字段键值对
     * @return 消息 ID
     */
    public RecordId addStream(String key, Map<String, String> fields) {
        return stringRedisTemplate.opsForStream().add(MapRecord.create(key, fields));
    }

    /**
     * 从 Stream 中读取消息
     *
     * @param key 键
     * @return 消息记录列表
     */
    public List<MapRecord<String, Object, Object>> readStream(String key) {
        return stringRedisTemplate.opsForStream().read(StreamOffset.fromStart(key));
    }
}
