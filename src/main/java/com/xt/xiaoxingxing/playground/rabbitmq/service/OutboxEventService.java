package com.xt.xiaoxingxing.playground.rabbitmq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqLearningProperties;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rabbitmq.mapper.MqOutboxEventMapper;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitMessageCodec;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Transactional Outbox 的事务写入和状态管理。 */
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final MqOutboxEventMapper outboxEventMapper;
    private final RabbitMessageCodec messageCodec;
    private final RabbitMqLearningProperties properties;

    /**
     * 在调用方当前 PostgreSQL 事务中追加待发布事件。
     *
     * <p>这里故意不创建新事务。创建订单的事务回滚时，Outbox 也必须一起回滚；否则会出现订单根本不存在，
     * RabbitMQ 却收到 ORDER_CREATED 的反向不一致。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.MANDATORY)
    public String append(String aggregateId,
                         String eventType,
                         String exchange,
                         String routingKey,
                         Object payload) {
        RabbitMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(eventType, aggregateId, payload);

        MqOutboxEvent event = new MqOutboxEvent();
        event.setId(envelope.getMessageId());
        event.setAggregateType("ORDER");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setSchemaVersion(envelope.getSchemaVersion());
        event.setExchangeName(exchange);
        event.setRoutingKey(routingKey);
        event.setPayload(messageCodec.toJson(envelope));
        event.setNextRetryAt(LocalDateTime.now());
        event.setCreatedAt(envelope.getOccurredAt());

        BusinessAssert.isTrue(outboxEventMapper.insert(event) == 1, "Outbox事件写入失败");
        return event.getId();
    }

    /**
     * 在一个很短的新事务中原子认领事件。
     *
     * <p>网络发送不能放在认领事务里，否则等待 RabbitMQ Confirm 时会一直持有 PostgreSQL 行锁。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public List<MqOutboxEvent> claimPublishable() {
        LocalDateTime lockExpiredBefore = LocalDateTime.now()
                .minusSeconds(properties.getOutbox().getLockTimeoutSeconds());
        return outboxEventMapper.claimPublishable(properties.getOutbox().getBatchSize(), lockExpiredBefore);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String eventId) {
        BusinessAssert.isTrue(outboxEventMapper.markPublished(eventId) == 1,
                "Outbox发布成功状态更新失败: " + eventId);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markFailed(MqOutboxEvent event, String error) {
        int currentRetry = event.getRetryCount() == null ? 0 : event.getRetryCount();

        // 5、10、20、40……秒指数退避，最高 5 分钟，避免 Broker 故障时持续高频打满日志和连接。
        long delaySeconds = Math.min(300L, 5L * (1L << Math.min(currentRetry, 6)));
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        String conciseError = error == null ? "未知发布错误" : error.substring(0, Math.min(error.length(), 1000));

        BusinessAssert.isTrue(outboxEventMapper.markFailed(
                        event.getId(), conciseError, nextRetryAt,
                        properties.getOutbox().getMaxPublishRetries()) == 1,
                "Outbox发布失败状态更新失败: " + event.getId());
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

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum > 0, "pageNum必须大于0");
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 100, "pageSize必须在1到100之间");
    }
}
