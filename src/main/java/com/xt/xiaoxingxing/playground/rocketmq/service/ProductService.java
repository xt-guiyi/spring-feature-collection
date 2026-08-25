package com.xt.xiaoxingxing.playground.rocketmq.service;

import tools.jackson.databind.json.JsonMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.OrderMqProperties;
import com.xt.xiaoxingxing.playground.rocketmq.dto.ProductResponse;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Order;
import com.xt.xiaoxingxing.playground.rocketmq.entity.OrderItem;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Product;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.OrderMapper;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** 商品服务。 */
@Slf4j
@Service
public class ProductService {

    private static final String CACHE_KEY_PREFIX = "playground:product:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;
    private final OrderMapper orderMapper;
    private final OrderMqProperties properties;

    public ProductService(StringRedisTemplate stringRedisTemplate,
                          JsonMapper jsonMapper,
                          OrderMapper orderMapper,
                          OrderMqProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
        this.orderMapper = orderMapper;
        this.properties = properties;
    }

    /** 查询商品信息。 */
    public ProductResponse getProduct(Long productId) {
        String key = cacheKey(productId);

        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(key);
            if (cachedJson != null && !cachedJson.isBlank()) {
                Product cached = jsonMapper.readValue(cachedJson, Product.class);
                return ProductResponse.from(cached, true);
            }
        } catch (Exception cacheReadFailure) {
            log.warn("读取商品缓存失败，已降级查询PostgreSQL: key={}, reason={}",
                    key, concise(cacheReadFailure));
        }

        Product product = BusinessAssert.notNull(orderMapper.selectProductById(productId), "商品不存在");
        try {
            String json = jsonMapper.writeValueAsString(product);
            stringRedisTemplate.opsForValue().set(
                    key, json, Duration.ofSeconds(properties.getProductCacheTtlSeconds()));
        } catch (Exception cacheWriteFailure) {
            log.warn("回填商品缓存失败，本次仍返回PostgreSQL结果: key={}, reason={}",
                    key, concise(cacheWriteFailure));
        }
        return ProductResponse.from(product, false);
    }

    /** 删除订单相关的商品缓存。 */
    public void evictOrderProducts(String orderNo) {
        Order order = BusinessAssert.notNull(orderMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
        List<String> keys = orderMapper.selectOrderItems(order.getId()).stream()
                .map(OrderItem::getProductId)
                .distinct()
                .sorted()
                .map(this::cacheKey)
                .toList();
        stringRedisTemplate.delete(keys);
    }

    /** 生成商品缓存键。 */
    private String cacheKey(Long productId) {
        return CACHE_KEY_PREFIX + productId;
    }

    /** 获取异常摘要。 */
    private String concise(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
