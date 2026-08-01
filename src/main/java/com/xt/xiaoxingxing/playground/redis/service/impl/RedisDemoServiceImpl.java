package com.xt.xiaoxingxing.playground.redis.service.impl;

import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.playground.redis.dto.request.RedisHashSetRequest;
import com.xt.xiaoxingxing.playground.redis.dto.request.RedisSetRequest;
import com.xt.xiaoxingxing.playground.redis.dto.response.RedisValueResponse;
import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.playground.redis.enums.RedisDemoStatus;
import com.xt.xiaoxingxing.playground.redis.repository.RedisDemoRepository;
import com.xt.xiaoxingxing.playground.redis.service.RedisDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisDemoServiceImpl implements RedisDemoService {

    private final RedisDemoRepository redisDemoRepository;

    @Override
    public RedisValueResponse set(RedisSetRequest request) {
        if (request.getExpireSeconds() != null && request.getExpireSeconds() > 0) {
            redisDemoRepository.set(request.getKey(), request.getValue(), request.getExpireSeconds(), TimeUnit.SECONDS);
        } else {
            redisDemoRepository.set(request.getKey(), request.getValue());
        }
        return get(request.getKey());
    }

    @Override
    public RedisValueResponse get(String key) {
        Object value = redisDemoRepository.get(key);
        if (value == null) {
            throw new BusinessException("key 不存在");
        }
        RedisValueResponse response = new RedisValueResponse();
        response.setKey(key);
        response.setValue(value.toString());
        response.setTtl(redisDemoRepository.getExpire(key));
        return response;
    }

    @Override
    public Boolean delete(String key) {
        return redisDemoRepository.delete(key);
    }

    @Override
    public RedisDemo setObject(RedisDemo demo) {
        demo.setCreateTime(LocalDateTime.now());
        demo.setStatus(RedisDemoStatus.ACTIVE);
        redisDemoRepository.setObject("demo:object:" + demo.getId(), demo);
        return demo;
    }

    @Override
    public RedisDemo getObject(String key) {
        RedisDemo demo = redisDemoRepository.getObject(key);
        if (demo == null) {
            throw new BusinessException("对象不存在");
        }
        return demo;
    }

    @Override
    public String setHash(RedisHashSetRequest request) {
        redisDemoRepository.setHash(request.getHashKey(), request.getField(), request.getValue());
        return "ok";
    }

    @Override
    public Object getHash(String hashKey, String field) {
        Object value = redisDemoRepository.getHash(hashKey, field);
        if (value == null) {
            throw new BusinessException("hash 字段不存在");
        }
        return value;
    }
}
