package com.xt.xiaoxingxing.playground.rocketmq.product;

import tools.jackson.databind.json.JsonMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProduct;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 商品库存的 Cache Aside 业务服务。
 *
 * <p>PostgreSQL 始终是价格和库存的权威事实源；Redis 只保存查询快照。查询链允许 Redis 故障时降级数据库，
 * 订单事件触发的缓存删除则必须把 Redis 异常抛给 Listener，让 RocketMQ 继续重试。两个场景不能使用同一套
 * “吞异常”策略，否则删除失败会被错误确认并长期保留陈旧库存。</p>
 */
@Slf4j
@Service
public class ProductService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final RocketMqLearningProperties properties;

    public ProductService(StringRedisTemplate stringRedisTemplate,
                          JsonMapper jsonMapper,
                          MqOrderBusinessMapper orderBusinessMapper,
                          RocketMqLearningProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
        this.orderBusinessMapper = orderBusinessMapper;
        this.properties = properties;
    }

    /**
     * Cache Aside 查询步骤：
     * <ol>
     *     <li>按配置前缀读取 Redis；合法命中时直接返回；</li>
     *     <li>未命中、JSON 损坏或 Redis 异常时查询 PostgreSQL；</li>
     *     <li>使用配置 TTL 尽力回填缓存；回填失败不影响本次数据库结果；</li>
     *     <li>返回商品业务响应，并用 cacheHit 帮助学习时观察路径。</li>
     * </ol>
     */
    public ProductResponse getProduct(Long productId) {
        BusinessAssert.isTrue(productId != null && productId > 0, "productId必须大于0");
        String key = cacheKey(productId);

        // 第1步：Redis 连接失败、超时或 JSON 损坏都按未命中处理，不能让查询接口失去数据库降级能力。
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(key);
            if (cachedJson != null && !cachedJson.isBlank()) {
                PgProduct cached = jsonMapper.readValue(cachedJson, PgProduct.class);
                if (isValidCachedProduct(cached, productId)) {
                    return ProductResponse.from(cached, true);
                }
                log.warn("商品缓存内容不完整，降级查询PostgreSQL并覆盖缓存: key={}", key);
            }
        } catch (Exception cacheReadFailure) {
            log.warn("读取商品缓存失败，已降级查询PostgreSQL: key={}, reason={}",
                    key, concise(cacheReadFailure));
        }

        // 第2步：数据库也失败时不能伪造空对象；商品不存在或 PostgreSQL 异常应正常交给接口错误处理。
        PgProduct product = BusinessAssert.notNull(
                orderBusinessMapper.selectProductById(productId), "商品不存在");

        // 第3步：回填只影响后续命中率。TTL 限制极端并发窗口中陈旧快照的最长存活时间，不能替代 MQ 失效。
        try {
            String json = jsonMapper.writeValueAsString(product);
            stringRedisTemplate.opsForValue().set(
                    key, json, Duration.ofSeconds(properties.getProductCache().getTtlSeconds()));
        } catch (Exception cacheWriteFailure) {
            log.warn("回填商品缓存失败，本次仍返回PostgreSQL结果: key={}, reason={}",
                    key, concise(cacheWriteFailure));
        }
        return ProductResponse.from(product, false);
    }

    /**
     * 删除库存发生变化的商品缓存。
     *
     * <p>本方法故意不捕获 Redis 异常。订单事件 Handler 只有在删除成功后才登记消费幂等记录；删除失败必须
     * 一直抛到 Listener，使 Broker 重投。重复删除不存在的键仍然成功，因此该操作天然幂等。</p>
     */
    public void evictProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        List<String> keys = productIds.stream()
                .filter(productId -> productId != null && productId > 0)
                .distinct()
                .sorted()
                .map(this::cacheKey)
                .toList();
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private String cacheKey(Long productId) {
        return properties.getProductCache().getKeyPrefix() + productId;
    }

    private boolean isValidCachedProduct(PgProduct product, Long expectedId) {
        return product != null
                && expectedId.equals(product.getId())
                && product.getName() != null && !product.getName().isBlank()
                && product.getPrice() != null
                && product.getStock() != null;
    }

    private String concise(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
