package com.xt.xiaoxingxing.playground.redis.service.impl;

import com.xt.xiaoxingxing.playground.redis.dto.request.*;
import com.xt.xiaoxingxing.playground.redis.dto.response.RedisValueResponse;
import com.xt.xiaoxingxing.playground.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.playground.redis.enums.RedisDemoStatus;
import com.xt.xiaoxingxing.playground.redis.repository.RedisDemoRepository;
import com.xt.xiaoxingxing.playground.redis.service.RedisDemoService;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metric;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisDemoServiceImpl implements RedisDemoService {

    private final RedisDemoRepository redisDemoRepository;

    @Override
    public RedisValueResponse set(RedisSetRequest request) {
        if (request.getExpireSeconds() != null && request.getExpireSeconds() > 0) {
            redisDemoRepository.set(request.getKey(), request.getValue(), request.getExpireSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        } else {
            redisDemoRepository.set(request.getKey(), request.getValue());
        }
        return get(request.getKey());
    }

    @Override
    public RedisValueResponse get(String key) {
        String value = redisDemoRepository.get(key);
        if (value == null) {
            throw new BusinessException("key 不存在");
        }
        RedisValueResponse response = new RedisValueResponse();
        response.setKey(key);
        response.setValue(value);
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
        return redisDemoRepository.getObject(key);
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

    @Override
    public Long leftPush(RedisListRequest request) {
        return redisDemoRepository.leftPush(request.getKey(), request.getValue());
    }

    @Override
    public Long rightPush(RedisListRequest request) {
        return redisDemoRepository.rightPush(request.getKey(), request.getValue());
    }

    @Override
    public String leftPop(String key) {
        String value = redisDemoRepository.leftPop(key);
        if (value == null) {
            throw new BusinessException("list 为空或不存在");
        }
        return value;
    }

    @Override
    public List<String> listRange(String key, long start, long end) {
        return redisDemoRepository.listRange(key, start, end);
    }

    @Override
    public Long addSet(RedisSetAddRequest request) {
        return redisDemoRepository.addSet(request.getKey(), request.getValue());
    }

    @Override
    public Set<String> members(String key) {
        return redisDemoRepository.members(key);
    }

    @Override
    public Boolean addZSet(RedisZSetRequest request) {
        return redisDemoRepository.addZSet(request.getKey(), request.getValue(), request.getScore());
    }

    @Override
    public Set<String> rangeZSet(String key, long start, long end) {
        return redisDemoRepository.rangeZSet(key, start, end);
    }

    @Override
    public Boolean setBit(RedisBitmapRequest request) {
        return redisDemoRepository.setBit(request.getKey(), request.getOffset(), request.getValue());
    }

    @Override
    public Boolean getBit(String key, long offset) {
        return redisDemoRepository.getBit(key, offset);
    }

    @Override
    public Long addHyperLogLog(RedisHyperLogLogRequest request) {
        return redisDemoRepository.addHyperLogLog(request.getKey(), request.getValue());
    }

    @Override
    public Long countHyperLogLog(String key) {
        return redisDemoRepository.countHyperLogLog(key);
    }

    @Override
    public Long addGeo(RedisGeoRequest request) {
        return redisDemoRepository.addGeo(request.getKey(), request.getLongitude(), request.getLatitude(), request.getMember());
    }

    @Override
    public Distance distanceGeo(String key, String member1, String member2, Metric metric) {
        Distance distance = redisDemoRepository.distanceGeo(key, member1, member2, metric);
        if (distance == null) {
            throw new BusinessException("无法计算距离，请确认成员是否存在");
        }
        return distance;
    }

    @Override
    public RecordId addStream(RedisStreamRequest request) {
        return redisDemoRepository.addStream(request.getKey(), request.getFields());
    }

    @Override
    public List<MapRecord<String, Object, Object>> readStream(String key) {
        return redisDemoRepository.readStream(key);
    }
}
