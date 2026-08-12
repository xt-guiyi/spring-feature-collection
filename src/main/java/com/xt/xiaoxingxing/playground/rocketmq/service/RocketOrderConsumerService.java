package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqNotificationLog;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqTransactionRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.support.MessageDecodingException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 不同订单 ConsumerGroup 的幂等副作用，以及付款超时的并发状态裁决。 */
@Service
@RequiredArgsConstructor
public class RocketOrderConsumerService {

    private final MqConsumerRecordMapper consumerRecordMapper;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final MqTransactionRecordMapper transactionRecordMapper;
    private final OutboxEventService outboxEventService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 删除商品缓存。数据库幂等记录和 Redis 无法组成一个本地事务，但删除操作本身可以安全重复。
     * Redis 成功而数据库提交失败时，Broker 重试只会再次删除相同键；Redis 失败则数据库事务回滚。
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleCache(RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：唯一约束裁决同组并发重复；不同 ConsumerGroup 使用不同 consumerName，各自仍会处理一份。
        if (!claim(envelope, RocketMqNames.ORDER_CACHE_GROUP)) {
            return ConsumeBusinessResult.DUPLICATE;
        }
        // 第2步：先校验 payload，再执行可安全重试的缓存失效。
        OrderEventPayload payload = toOrderPayload(envelope);
        List<String> keys = payload.getProductIds() == null ? List.of() : payload.getProductIds().stream()
                .map(id -> "playground:product:" + id).toList();
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        return ConsumeBusinessResult.PROCESSED;
    }

    /** 消费幂等记录与统计 UPSERT 同事务提交，防止至少一次投递造成重复累计。 */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleStatistics(RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：只有首次插入幂等记录的一方拥有更新统计的资格。
        if (!claim(envelope, RocketMqNames.ORDER_STATISTICS_GROUP)) {
            return ConsumeBusinessResult.DUPLICATE;
        }
        // 第2步：数据库 UPSERT 原子累计；异常会让领取记录一起回滚，Broker 重试后仍可继续处理。
        OrderEventPayload payload = toOrderPayload(envelope);
        BusinessAssert.isTrue(consumerRecordMapper.upsertStatistics(
                        envelope.getEventType(), payload.getTotalAmount(), envelope.getOccurredAt()) == 1,
                "订单统计更新失败");
        return ConsumeBusinessResult.PROCESSED;
    }

    /** 模拟通知落库；真实外部短信平台还应接收 messageId 作为幂等键。 */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleNotification(RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：同一通知组对同一业务 messageId 只执行一次。
        if (!claim(envelope, RocketMqNames.ORDER_NOTIFICATION_GROUP)) {
            return ConsumeBusinessResult.DUPLICATE;
        }
        // 第2步：通知日志与幂等记录同事务提交，失败返回 FAILURE 后由 Broker 重试，耗尽后进入该组 DLQ。
        OrderEventPayload payload = toOrderPayload(envelope);
        insertNotification(envelope, payload, "ORDER_EVENT");
        return ConsumeBusinessResult.PROCESSED;
    }

    /**
     * 延迟消息到达后取消仍未支付的订单。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>领取消费幂等键，重复投递直接成功；</li>
     *     <li>重读订单当前状态，不信任旧消息快照；</li>
     *     <li>以条件更新竞争 PENDING 到 CANCELLED；</li>
     *     <li>只有竞争成功者按商品 ID 顺序恢复库存；</li>
     *     <li>在同一事务追加 ORDER_CANCELLED Outbox。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleTimeout(RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：RocketMQ 是至少一次投递；唯一约束是跨线程、跨实例的最终幂等兜底。
        if (!claim(envelope, RocketMqNames.ORDER_TIMEOUT_GROUP)) {
            return ConsumeBusinessResult.DUPLICATE;
        }

        // 第2步：消息生成后订单可能已经支付或被取消，过期事件应幂等视为成功，而不是无意义重试。
        OrderEventPayload source = toOrderPayload(envelope);
        PgOrder order = orderBusinessMapper.selectOrderById(source.getOrderId());
        if (order == null || !"PENDING".equals(order.getStatus())) {
            return ConsumeBusinessResult.STALE;
        }

        // 第3步：读取后仍有并发窗口，真正裁决必须是带 WHERE status='PENDING' 的原子条件更新。
        if (orderBusinessMapper.markCancelled(order.getId()) != 1) {
            return ConsumeBusinessResult.STALE;
        }

        // 第4步：只有状态竞争获胜者恢复库存；固定锁顺序可降低多订单交叉恢复时的死锁概率。
        List<PgOrderProduct> items = orderBusinessMapper.selectOrderProducts(order.getId()).stream()
                .sorted(Comparator.comparing(PgOrderProduct::getProductId)).toList();
        for (PgOrderProduct item : items) {
            BusinessAssert.isTrue(orderBusinessMapper.restoreStock(item.getProductId(), item.getQuantity()) == 1,
                    "恢复库存时商品不存在: " + item.getProductId());
        }

        // 第5步：取消事件仍使用 Outbox；若本事务回滚，状态、库存、幂等记录和消息意图会一起回滚。
        OrderEventPayload cancelled = RocketOrderApplicationService.buildPayload(order, items);
        outboxEventService.append(String.valueOf(order.getId()), RocketMqNames.EVENT_ORDER_CANCELLED,
                RocketMqNames.NORMAL_TOPIC, RocketMqNames.TAG_ORDER_CANCELLED,
                order.getOrderNo(), null, null, cancelled);
        return ConsumeBusinessResult.PROCESSED;
    }

    /**
     * 已提交的事务半消息只在一个独立组内执行一次统计和模拟通知。
     *
     * <p>实现步骤：第1步领取组内幂等键；第2步通过持久事务记录读取最终订单事实；
     * 第3步在一个本地事务完成统计、通知和幂等记录。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public ConsumeBusinessResult handleTransactionOrder(RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：半消息 COMMIT 后仍遵循至少一次消费，必须先领取该事务消费者的唯一幂等键。
        if (!claim(envelope, RocketMqNames.TRANSACTION_ORDER_GROUP)) {
            return ConsumeBusinessResult.DUPLICATE;
        }
        // 第2步：消息内只有持久命令；以 COMMITTED 记录关联的 orderId 读取最终订单事实，不相信内存状态。
        MqTransactionRecord record = BusinessAssert.notNull(
                transactionRecordMapper.selectById(envelope.getAggregateId()), "事务消息缺少持久化回查记录");
        BusinessAssert.isTrue("COMMITTED".equals(record.getStatus()) && record.getOrderId() != null,
                "事务消息尚无已提交订单事实");
        PgOrder order = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(record.getOrderId()), "事务订单不存在");
        OrderEventPayload payload = RocketOrderApplicationService.buildPayload(
                order, orderBusinessMapper.selectOrderProducts(order.getId()));
        // 第3步：统计和模拟通知与幂等记录同事务；失败会整体回滚并由 Broker 重试，最终由该消费组 DLQ 收口。
        BusinessAssert.isTrue(consumerRecordMapper.upsertStatistics(
                        RocketMqNames.EVENT_ORDER_CREATED, payload.getTotalAmount(), envelope.getOccurredAt()) == 1,
                "事务订单统计更新失败");
        insertNotification(envelope, payload, "TRANSACTION_MESSAGE");
        return ConsumeBusinessResult.PROCESSED;
    }

    private boolean claim(RocketMessageEnvelope<?> envelope, String consumerName) {
        MqConsumedMessage consumed = new MqConsumedMessage();
        consumed.setConsumerName(consumerName);
        consumed.setMessageId(envelope.getMessageId());
        consumed.setEventType(envelope.getEventType());
        consumed.setAggregateId(envelope.getAggregateId());
        consumed.setConsumedAt(LocalDateTime.now());
        return consumerRecordMapper.insertConsumedIfAbsent(consumed) == 1;
    }

    private OrderEventPayload toOrderPayload(RocketMessageEnvelope<?> envelope) {
        try {
            OrderEventPayload payload = objectMapper.treeToValue(
                    (JsonNode) envelope.getPayload(), OrderEventPayload.class);
            /*
             * JSON 能反序列化不代表业务协议完整。比如缺少 orderId 的超时消息会被 SQL 当作“查不到订单”，
             * 如果直接返回 STALE，就会把损坏消息错误确认为成功并永久丢失排查证据。
             * 因此必填快照字段必须在执行任何幂等副作用前校验；失败由公共模板返回 FAILURE，最终进入 DLQ。
             */
            boolean valid = payload != null
                    && payload.getOrderId() != null && payload.getOrderId() > 0
                    && payload.getOrderNo() != null && !payload.getOrderNo().isBlank()
                    && payload.getUserId() != null && payload.getUserId() > 0
                    && payload.getTotalAmount() != null && payload.getTotalAmount().signum() >= 0
                    && payload.getItemCount() != null && payload.getItemCount() > 0
                    && payload.getProductIds() != null && !payload.getProductIds().isEmpty()
                    && payload.getProductIds().stream().allMatch(id -> id != null && id > 0);
            if (!valid) {
                throw new MessageDecodingException(
                        "订单事件payload缺少合法的orderId、orderNo、userId、金额、商品数量或productIds");
            }
            return payload;
        } catch (JsonProcessingException | ClassCastException e) {
            throw new MessageDecodingException("订单事件payload格式不正确", e);
        }
    }

    private void insertNotification(RocketMessageEnvelope<?> envelope,
                                    OrderEventPayload payload,
                                    String source) {
        MqNotificationLog notification = new MqNotificationLog();
        notification.setMessageId(envelope.getMessageId());
        notification.setOrderId(payload.getOrderId());
        notification.setEventType(envelope.getEventType());
        notification.setChannel("SIMULATED_SMS");
        notification.setStatus("SENT");
        notification.setContent("模拟通知[" + source + "]：订单 " + payload.getOrderNo()
                + "，事件 " + envelope.getEventType() + "，金额 " + payload.getTotalAmount());
        notification.setCreatedAt(LocalDateTime.now());
        BusinessAssert.isTrue(consumerRecordMapper.insertNotification(notification) == 1,
                "模拟通知写入失败或出现同消息重复渠道记录");
    }
}
