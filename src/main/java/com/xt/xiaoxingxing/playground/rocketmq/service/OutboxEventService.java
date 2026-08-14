package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOutboxEventMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Transactional Outbox 的事务写入、短租约领取和状态推进服务。 */
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final MqOutboxEventMapper outboxEventMapper;
    private final RocketMessageCodec messageCodec;
    private final RocketMqLearningProperties properties;

    /**
     * 在调用方已经开启的订单事务中追加事件。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>发送前生成稳定业务消息 ID 和版本化信封；</li>
     *     <li>保存 Topic、Tag、Key、MessageGroup 和投递时间；</li>
     *     <li>检查插入行数，失败就让整个订单事务回滚。</li>
     * </ol>
     *
     * <p>{@code MANDATORY} 会拒绝没有外层事务的调用，防止 Outbox 独立提交后却找不到对应订单。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.MANDATORY)
    public String append(String aggregateId,
                         String eventType,
                         String topic,
                         String tag,
                         String messageKey,
                         String messageGroup,
                         LocalDateTime deliverAt,
                         Object payload) {
        // 第1步：messageId 在第一次发布前生成；同一 Outbox 的所有重试都复用它，消费者才能识别重复。
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(eventType, aggregateId, payload);

        // 第2步：保存 RocketMQ 的寻址语义。MessageGroup 只对 FIFO 有意义，普通和延迟消息允许为空。
        MqOutboxEvent event = new MqOutboxEvent();
        event.setId(envelope.getMessageId());
        event.setAggregateType("ORDER");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setSchemaVersion(envelope.getSchemaVersion());
        event.setTopicName(topic);
        event.setMessageTag(tag);
        event.setMessageKey(messageKey);
        event.setMessageGroup(messageGroup);
        event.setDeliverAt(deliverAt);
        event.setPayload(messageCodec.toJson(envelope));
        event.setNextRetryAt(LocalDateTime.now());
        event.setCreatedAt(envelope.getOccurredAt());

        // 第3步：插入失败必须抛异常，订单、库存和 Outbox 随同一个 PostgreSQL 事务一起回滚。
        BusinessAssert.isTrue(outboxEventMapper.insert(event) == 1, "Outbox事件写入失败");
        return event.getId();
    }

    /** 原子领取由 SQL 的 FOR UPDATE SKIP LOCKED 完成，网络发送不会占着数据库行锁。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public List<MqOutboxEvent> claimPublishable() {
        LocalDateTime lockExpiredBefore = LocalDateTime.now()
                .minusSeconds(properties.getOutbox().getLockTimeoutSeconds());
        return outboxEventMapper.claimPublishable(properties.getOutbox().getBatchSize(), lockExpiredBefore);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(MqOutboxEvent event) {
        return outboxEventMapper.markPublished(event.getId(), event.getLockedAt()) == 1;
    }

    /** 发布失败采用指数退避；达到上限后由 SQL 条件推进到 DEAD，等待人工检查。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(MqOutboxEvent event, String error) {
        int currentRetry = event.getRetryCount() == null ? 0 : event.getRetryCount();
        RocketMqLearningProperties.Retry retry = properties.getOutbox().getRetry();
        // 第1次失败等待 initialDelay；随后指数翻倍；达到 maxExponent 后不再扩大，且永远不超过 maxDelay。
        long multiplier = 1L << Math.min(currentRetry, retry.getMaxExponent());
        long maxDelaySeconds = retry.getMaxDelaySeconds();
        long initialDelaySeconds = retry.getInitialDelaySeconds();
        // 先用除法判断乘法是否会超过上限，避免配置较大时 long 溢出成负数，反而生成过去的重试时间。
        long delaySeconds = initialDelaySeconds > maxDelaySeconds / multiplier
                ? maxDelaySeconds
                : Math.min(maxDelaySeconds, initialDelaySeconds * multiplier);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        String conciseError = concise(error);
        return outboxEventMapper.markFailed(
                        event.getId(), event.getLockedAt(), conciseError, nextRetryAt,
                        properties.getOutbox().getMaxPublishRetries()) == 1;
    }

    public PageResult<MqOutboxEvent> page(String status, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        long offset = (long) (pageNum - 1) * pageSize;
        PageResult<MqOutboxEvent> result = new PageResult<>();
        result.setList(outboxEventMapper.selectPage(status, offset, pageSize));
        result.setTotal(outboxEventMapper.countPage(status));
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    public MqOutboxEvent getById(String id) {
        return BusinessAssert.notNull(outboxEventMapper.selectById(id), "Outbox事件不存在");
    }

    private String concise(String error) {
        String value = error == null || error.isBlank() ? "未知发布错误" : error;
        return value.substring(0, Math.min(value.length(), 1000));
    }

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum > 0, "pageNum必须大于0");
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 100, "pageSize必须在1到100之间");
    }
}
