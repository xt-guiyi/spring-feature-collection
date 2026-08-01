package com.xt.xiaoxingxing.playground.redis.service;

import com.xt.xiaoxingxing.playground.redis.dto.request.*;
import com.xt.xiaoxingxing.playground.redis.dto.response.RedisValueResponse;
import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metric;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.util.List;
import java.util.Set;

public interface RedisDemoService {

    // String
    RedisValueResponse set(RedisSetRequest request);

    RedisValueResponse get(String key);

    Boolean delete(String key);

    // Object
    RedisDemo setObject(RedisDemo demo);

    RedisDemo getObject(String key);

    // Hash
    String setHash(RedisHashSetRequest request);

    Object getHash(String hashKey, String field);

    // List
    Long leftPush(RedisListRequest request);

    Long rightPush(RedisListRequest request);

    String leftPop(String key);

    List<String> listRange(String key, long start, long end);

    // Set
    Long addSet(RedisSetAddRequest request);

    Set<String> members(String key);

    // ZSet
    Boolean addZSet(RedisZSetRequest request);

    Set<String> rangeZSet(String key, long start, long end);

    // Bitmap
    Boolean setBit(RedisBitmapRequest request);

    Boolean getBit(String key, long offset);

    // HyperLogLog
    Long addHyperLogLog(RedisHyperLogLogRequest request);

    Long countHyperLogLog(String key);

    // Geo
    Long addGeo(RedisGeoRequest request);

    Distance distanceGeo(String key, String member1, String member2, Metric metric);

    // Stream
    RecordId addStream(RedisStreamRequest request);

    List<MapRecord<String, Object, Object>> readStream(String key);
}
