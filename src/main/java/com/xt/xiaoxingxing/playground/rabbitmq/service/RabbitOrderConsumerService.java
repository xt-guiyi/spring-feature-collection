package com.xt.xiaoxingxing.playground.rabbitmq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqNotificationLog;
import com.xt.xiaoxingxing.playground.rabbitmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rabbitmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rabbitmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import com.xt.xiaoxingxing.playground.rabbitmq.support.MessageDecodingException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 可靠订单各消费者真正执行的幂等业务。 */
@Service
@RequiredArgsConstructor
public class RabbitOrderConsumerService {

    private final MqConsumerRecordMapper consumerRecordMapper;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final OutboxEventService outboxEventService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 删除商品缓存。
     *
     * <p>消费记录与 Redis 不能组成一个本地事务。这里先在 PostgreSQL 插入幂等记录，再删除缓存；如果 Redis 报错，
     * PostgreSQL 事务回滚，RabbitMQ 稍后重试。如果 Redis 成功但 PostgreSQL 提交失败，重试只会再次删除相同键，
     * 删除操作本身是幂等的。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleCache(RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope) {
        if (!claim(envelope, RabbitMqNames.CACHE_CONSUMER)) {
            return ConsumeBusinessResult.DUPLICATE;
        }

        OrderEventPayload payload = toOrderPayload(envelope);
        List<String> keys = payload.getProductIds() == null ? List.of() : payload.getProductIds().stream()
                .map(productId -> "playground:product:" + productId)
                .toList();
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        return ConsumeBusinessResult.PROCESSED;
    }

    /** 幂等记录和统计 UPSERT 在同一个事务中，任意一步失败都会一起回滚。 */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleStatistics(
            RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope) {
        if (!claim(envelope, RabbitMqNames.STATISTICS_CONSUMER)) {
            return ConsumeBusinessResult.DUPLICATE;
        }

        OrderEventPayload payload = toOrderPayload(envelope);
        consumerRecordMapper.upsertStatistics(
                envelope.getEventType(), payload.getTotalAmount(), envelope.getOccurredAt());
        return ConsumeBusinessResult.PROCESSED;
    }

    /** 模拟通知使用数据库日志代替真实短信平台，并把 messageId 当作外部幂等键。 */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleNotification(
            RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope) {
        if (!claim(envelope, RabbitMqNames.NOTIFICATION_CONSUMER)) {
            return ConsumeBusinessResult.DUPLICATE;
        }

        OrderEventPayload payload = toOrderPayload(envelope);
        MqNotificationLog notification = new MqNotificationLog();
        notification.setMessageId(envelope.getMessageId());
        notification.setOrderId(payload.getOrderId());
        notification.setEventType(envelope.getEventType());
        notification.setChannel("SIMULATED_SMS");
        notification.setStatus("SENT");
        notification.setContent("模拟通知：订单 " + payload.getOrderNo() + " 已创建，待支付金额 "
                + payload.getTotalAmount());
        notification.setCreatedAt(LocalDateTime.now());
        consumerRecordMapper.insertNotification(notification);
        return ConsumeBusinessResult.PROCESSED;
    }

    /**
     * 处理 30 分钟未支付检查。
     *
     * <p>完整步骤：</p>
     * <ol>
     *     <li>写入消费者幂等记录，重复消息直接返回；</li>
     *     <li>重新读取订单当前状态，不能相信 30 分钟前的消息快照；</li>
     *     <li>用条件 UPDATE 抢占 PENDING -> CANCELLED 状态转换；</li>
     *     <li>只有本事务抢占成功，才按 productId 顺序恢复库存；</li>
     *     <li>追加 ORDER_CANCELLED Outbox，由同一个 PostgreSQL 事务提交。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleTimeout(
            RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope) {
        // 第1步：数据库唯一键是并发重复投递的最终裁决。
        if (!claim(envelope, RabbitMqNames.TIMEOUT_CONSUMER)) {
            return ConsumeBusinessResult.DUPLICATE;
        }

        OrderEventPayload sourcePayload = toOrderPayload(envelope);
        Long orderId = sourcePayload.getOrderId();

        // 第2步：延迟消息到达时，订单可能已支付或已经被其他流程取消。
        PgOrder order = orderBusinessMapper.selectOrderById(orderId);
        if (order == null || !"PENDING".equals(order.getStatus())) {
            return ConsumeBusinessResult.STALE;
        }

        // 第3步：读取后的状态仍可能马上变化，真正裁决必须依赖带状态条件的一条 UPDATE。
        if (orderBusinessMapper.markCancelled(orderId) != 1) {
            return ConsumeBusinessResult.STALE;
        }

        // 第4步：只有上一步影响一行，本事务才拥有“恢复一次库存”的资格。
        List<PgOrderProduct> items = orderBusinessMapper.selectOrderProducts(orderId);
        for (PgOrderProduct item : items) {
            BusinessAssert.isTrue(orderBusinessMapper.restoreStock(item.getProductId(), item.getQuantity()) == 1,
                    "恢复库存时商品不存在: " + item.getProductId());
        }

        // 第5步：取消事件也走 Outbox，避免在消费者数据库事务中直接发送 RabbitMQ。
        OrderEventPayload cancelledPayload = buildCurrentPayload(order, items);
        outboxEventService.append(
                String.valueOf(orderId),
                RabbitMqNames.EVENT_ORDER_CANCELLED,
                RabbitMqNames.ORDER_EVENT_EXCHANGE,
                RabbitMqNames.ORDER_CANCELLED_KEY,
                cancelledPayload);
        return ConsumeBusinessResult.PROCESSED;
    }

    private boolean claim(RabbitMessageEnvelope<?> envelope, String consumerName) {
        MqConsumedMessage consumed = new MqConsumedMessage();
        consumed.setConsumerName(consumerName);
        consumed.setMessageId(envelope.getMessageId());
        consumed.setEventType(envelope.getEventType());
        consumed.setAggregateId(envelope.getAggregateId());
        consumed.setConsumedAt(LocalDateTime.now());
        return consumerRecordMapper.insertConsumedIfAbsent(consumed) == 1;
    }

    private OrderEventPayload toOrderPayload(RabbitMessageEnvelope<?> envelope) {
        try {
            return objectMapper.treeToValue(
                    (com.fasterxml.jackson.databind.JsonNode) envelope.getPayload(), OrderEventPayload.class);
        } catch (JsonProcessingException | ClassCastException e) {
            throw new MessageDecodingException("订单事件payload格式不正确", e);
        }
    }

    private OrderEventPayload buildCurrentPayload(PgOrder order, List<PgOrderProduct> items) {
        long itemCount = items.stream().map(PgOrderProduct::getQuantity).mapToLong(Integer::longValue).sum();
        BusinessAssert.isTrue(itemCount <= Integer.MAX_VALUE, "订单商品总数过大");

        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getId());
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(order.getUserId());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setItemCount((int) itemCount);
        payload.setProductIds(items.stream().map(PgOrderProduct::getProductId).toList());
        return payload;
    }
}
