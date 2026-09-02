package com.xt.xiaoxingxing.playground.features.redis.service.impl;

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
import com.xt.xiaoxingxing.playground.features.redis.entity.RedisDemo;
import com.xt.xiaoxingxing.playground.features.redis.enums.RedisDemoStatus;
import com.xt.xiaoxingxing.playground.features.redis.repository.RedisDemoRepository;
import com.xt.xiaoxingxing.playground.features.redis.service.RedisDemoService;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Metrics;
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
    public RedisDemoResponse setObject(RedisDemoRequest demo) {
        RedisDemo entity = new RedisDemo();
        entity.setId(demo.getId());
        entity.setName(demo.getName());
        entity.setCreateTime(LocalDateTime.now());
        entity.setStatus(RedisDemoStatus.ACTIVE);
        redisDemoRepository.setObject("demo:object:" + entity.getId(), entity);
        return toResponse(entity);
    }

    @Override
    public RedisDemoResponse getObject(String key) {
        return toResponse(redisDemoRepository.getObject(key));
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
    public RedisDistanceResponse distanceGeo(String key, String member1, String member2) {
        org.springframework.data.geo.Distance distance = redisDemoRepository.distanceGeo(
                key, member1, member2, Metrics.KILOMETERS);
        if (distance == null) {
            throw new BusinessException("无法计算距离，请确认成员是否存在");
        }
        RedisDistanceResponse response = new RedisDistanceResponse();
        response.setValue(distance.getValue());
        response.setMetric(distance.getMetric().getAbbreviation());
        response.setNormalizedValue(distance.getNormalizedValue());
        response.setUnit(distance.getUnit());
        return response;
    }

    @Override
    public RedisRecordIdResponse addStream(RedisStreamRequest request) {
        return toResponse(redisDemoRepository.addStream(request.getKey(), request.getFields()));
    }

    @Override
    public List<RedisStreamRecordResponse> readStream(String key) {
        return redisDemoRepository.readStream(key).stream().map(this::toResponse).toList();
    }

    private RedisDemoResponse toResponse(RedisDemo source) {
        if (source == null) {
            return null;
        }
        RedisDemoResponse target = new RedisDemoResponse();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setStatus(source.getStatus());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    private RedisRecordIdResponse toResponse(org.springframework.data.redis.connection.stream.RecordId source) {
        if (source == null) {
            return null;
        }
        RedisRecordIdResponse target = new RedisRecordIdResponse();
        target.setTimestamp(source.getTimestamp());
        target.setSequence(source.getSequence());
        target.setValue(source.getValue());
        return target;
    }

    private RedisStreamRecordResponse toResponse(
            org.springframework.data.redis.connection.stream.MapRecord<String, Object, Object> source) {
        RedisStreamRecordResponse target = new RedisStreamRecordResponse();
        target.setStream(source.getStream());
        target.setId(toResponse(source.getId()));
        target.setValue(source.getValue());
        return target;
    }
}
