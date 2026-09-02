package com.xt.xiaoxingxing.playground.features.redis.service;

import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisBitmapRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisGeoRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisHashSetRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisHyperLogLogRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisListRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisSetAddRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisSetRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisStreamRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisZSetRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.request.RedisDemoRequest;
import com.xt.xiaoxingxing.playground.features.redis.dto.response.RedisDemoResponse;
import com.xt.xiaoxingxing.playground.features.redis.dto.response.RedisDistanceResponse;
import com.xt.xiaoxingxing.playground.features.redis.dto.response.RedisRecordIdResponse;
import com.xt.xiaoxingxing.playground.features.redis.dto.response.RedisStreamRecordResponse;
import com.xt.xiaoxingxing.playground.features.redis.dto.response.RedisValueResponse;

import java.util.List;
import java.util.Set;

public interface RedisDemoService {

    // String
    RedisValueResponse set(RedisSetRequest request);

    RedisValueResponse get(String key);

    Boolean delete(String key);

    // Object
    RedisDemoResponse setObject(RedisDemoRequest demo);

    RedisDemoResponse getObject(String key);

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

    RedisDistanceResponse distanceGeo(String key, String member1, String member2);

    // Stream
    RedisRecordIdResponse addStream(RedisStreamRequest request);

    List<RedisStreamRecordResponse> readStream(String key);
}
