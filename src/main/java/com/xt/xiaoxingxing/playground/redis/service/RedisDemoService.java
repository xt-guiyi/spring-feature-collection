package com.xt.xiaoxingxing.playground.redis.service;

import com.xt.xiaoxingxing.playground.redis.dto.request.RedisHashSetRequest;
import com.xt.xiaoxingxing.playground.redis.dto.request.RedisSetRequest;
import com.xt.xiaoxingxing.playground.redis.dto.response.RedisValueResponse;
import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;

public interface RedisDemoService {

    RedisValueResponse set(RedisSetRequest request);

    RedisValueResponse get(String key);

    Boolean delete(String key);

    RedisDemo setObject(RedisDemo demo);

    RedisDemo getObject(String key);

    String setHash(RedisHashSetRequest request);

    Object getHash(String hashKey, String field);
}
