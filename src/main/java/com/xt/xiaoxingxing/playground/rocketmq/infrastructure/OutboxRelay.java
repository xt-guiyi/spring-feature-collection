package com.xt.xiaoxingxing.playground.rocketmq.infrastructure;

import tools.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOutboxEventMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PostgreSQL Outbox 到 RocketMQ 的可靠中继器。
 *
 * <p>Relay 不是订单业务 Service：它不创建订单、不扣减或恢复库存，只把数据库中已经提交的消息意图发布出去。
 * 一轮处理被明确拆成三个可靠性窗口：</p>
 * <ol>
 *     <li>短数据库事务原子领取一批事件并立即提交，释放行锁和连接；</li>
 *     <li>数据库事务之外同步调用 RocketMQ，网络等待期间不占用 PostgreSQL 事务；</li>
 *     <li>另一个短数据库事务携带 {@code id + lockedAt} 租约令牌回写 PUBLISHED、FAILED 或 DEAD。</li>
 * </ol>
 *
 * <p>Broker 已接收但成功回写前进程崩溃时，租约过期后事件会再次发布，因此本机制保证的是至少一次发布。
 * 消费者必须使用信封内稳定 messageId 做幂等，不能假设每条消息只到达一次。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
public class OutboxRelay {

    private final MqOutboxEventMapper outboxEventMapper;
    private final RocketMessageCodec messageCodec;
    private final RocketMessagePublisher publisher;
    private final RocketMqLearningProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean publishing = new AtomicBoolean(false);

    public OutboxRelay(MqOutboxEventMapper outboxEventMapper,
                       RocketMessageCodec messageCodec,
                       RocketMessagePublisher publisher,
                       RocketMqLearningProperties properties,
                       @Qualifier("playgroundTransactionManager")
                       PlatformTransactionManager transactionManager) {
        this.outboxEventMapper = outboxEventMapper;
        this.messageCodec = messageCodec;
        this.publisher = publisher;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Relay 不应意外加入调用方事务。每次领取或状态回写都必须是边界清晰、尽快结束的新事务。
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 每轮发布的完整步骤：
     * <ol>
     *     <li>防止同一 JVM 中上一轮尚未完成时再次进入；</li>
     *     <li>短事务领取一批 PENDING/FAILED 或租约过期的 PROCESSING 事件；</li>
     *     <li>领取事务结束后逐条同步发送，网络调用不持有 PostgreSQL 事务；</li>
     *     <li>每条消息再用独立短事务回写成功或失败状态；</li>
     *     <li>本轮异常只记录日志，后续调度继续接管尚未完成的事件。</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${playground.rocketmq.outbox.fixed-delay-millis}")
    public void publishPendingEvents() {
        // 第1步：AtomicBoolean 只减少同 JVM 的重叠调度；跨实例竞争仍由数据库 SKIP LOCKED 和租约裁决。
        if (!publishing.compareAndSet(false, true)) {
            log.debug("上一轮Outbox仍在发布，本轮跳过");
            return;
        }

        try {
            // 第2步：execute 返回前事务已经提交。后面的 publishOne 不在这个数据库事务中运行。
            List<MqOutboxEvent> events = claimPublishableInShortTransaction();
            for (MqOutboxEvent event : events) {
                publishOne(event);
            }
        } catch (Exception exception) {
            log.error("Outbox批量领取或发布异常，后续调度仍会继续", exception);
        } finally {
            publishing.set(false);
        }
    }

    /**
     * 发布一条已领取事件。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>从 JSONB 文本恢复并校验原始版本化信封；</li>
     *     <li>根据配置中的 NORMAL/DELAY Topic 选择同步发布 API；</li>
     *     <li>Broker 接收成功后，用新短事务和领取时的 lockedAt 标记 PUBLISHED；</li>
     *     <li>任何异常都计算指数退避，并用另一个短事务标记 FAILED 或达到上限后的 DEAD。</li>
     * </ol>
     */
    private void publishOne(MqOutboxEvent event) {
        try {
            // 第1步：协议损坏或版本不支持也属于发布失败；记录重试并最终进入 DEAD，不能静默丢弃。
            RocketMessageEnvelope<JsonNode> envelope = messageCodec.fromJson(event.getPayload());

            // 第2步：此处已经离开领取 TransactionTemplate，RocketMQ 网络等待不会占着数据库连接或行锁。
            String brokerMessageId = publishToBroker(event, envelope);

            // 第3步：发送期间租约可能已经过期并被另一个实例重新领取，因此必须比较原 lockedAt。
            if (markPublishedInShortTransaction(event)) {
                log.info("Outbox发布成功: eventId={}, brokerMessageId={}, topic={}, tag={}",
                        event.getId(), brokerMessageId, event.getTopicName(), event.getMessageTag());
            } else {
                log.warn("Outbox已被Broker接收但领取租约已过期，不覆盖新worker状态: eventId={}", event.getId());
            }
        } catch (Exception exception) {
            // 第4步：失败状态更新本身也可能失败；此时保留 PROCESSING，租约过期后仍能被重新领取。
            try {
                if (!markFailedInShortTransaction(event, concise(exception))) {
                    log.warn("Outbox异常但领取租约已过期，不覆盖新worker状态: eventId={}", event.getId());
                }
            } catch (Exception stateException) {
                log.error("Outbox异常且失败状态也无法保存: eventId={}", event.getId(), stateException);
            }
            log.error("Outbox事件处理异常: eventId={}", event.getId(), exception);
        }
    }

    /** 只做网络发布；本方法以及调用它的 publishOne 都没有数据库事务注解或 TransactionTemplate 包裹。 */
    private String publishToBroker(MqOutboxEvent event, RocketMessageEnvelope<JsonNode> envelope) {
        if (properties.getTopics().getDelay().equals(event.getTopicName())) {
            long minimumDelaySeconds = properties.getDelay().getMinimumBrokerDelaySeconds();
            long remainingMillis = event.getDeliverAt() == null ? 0L
                    : ChronoUnit.MILLIS.between(LocalDateTime.now(), event.getDeliverAt());
            // 向上取整：剩余 1 毫秒仍是 1 秒。事件已过期时由 minimumBrokerDelaySeconds 保证合法正延迟。
            long delaySeconds = Math.max(minimumDelaySeconds, Math.ceilDiv(remainingMillis, 1000L));
            return publisher.publishDelay(
                    event.getTopicName(), event.getMessageTag(), event.getMessageKey(), delaySeconds, envelope);
        }
        if (properties.getTopics().getNormal().equals(event.getTopicName())) {
            return publisher.publishNormal(
                    event.getTopicName(), event.getMessageTag(), event.getMessageKey(), envelope);
        }
        throw new IllegalArgumentException("Outbox不支持的Topic类型: " + event.getTopicName());
    }

    /** 原子领取 SQL 自带 FOR UPDATE SKIP LOCKED；事务提交后网络发送才开始。 */
    private List<MqOutboxEvent> claimPublishableInShortTransaction() {
        List<MqOutboxEvent> events = transactionTemplate.execute(status -> {
            LocalDateTime lockExpiredBefore = LocalDateTime.now()
                    .minusSeconds(properties.getOutbox().getLockTimeoutSeconds());
            return outboxEventMapper.claimPublishable(
                    properties.getOutbox().getBatchSize(), lockExpiredBefore);
        });
        return events == null ? List.of() : events;
    }

    /** 只有仍持有本次 lockedAt 租约的 worker 可以结束为 PUBLISHED。 */
    private boolean markPublishedInShortTransaction(MqOutboxEvent event) {
        Boolean updated = transactionTemplate.execute(status ->
                outboxEventMapper.markPublished(event.getId(), event.getLockedAt()) == 1);
        return Boolean.TRUE.equals(updated);
    }

    /**
     * 发布失败采用有上限的指数退避；SQL 原子增加 retry_count，并在达到 maxPublishRetries 时转为 DEAD。
     */
    private boolean markFailedInShortTransaction(MqOutboxEvent event, String error) {
        int currentRetry = event.getRetryCount() == null ? 0 : event.getRetryCount();
        RocketMqLearningProperties.Retry retry = properties.getOutbox().getRetry();

        // 第1次失败等待 initialDelay；随后指数翻倍。maxExponent 最大为 30，所以左移不会溢出 long。
        long multiplier = 1L << Math.min(currentRetry, retry.getMaxExponent());
        long maxDelaySeconds = retry.getMaxDelaySeconds();
        long initialDelaySeconds = retry.getInitialDelaySeconds();
        // 先用除法判断乘法是否超过上限，避免大配置相乘溢出为负数后生成过去的重试时间。
        long delaySeconds = initialDelaySeconds > maxDelaySeconds / multiplier
                ? maxDelaySeconds
                : Math.min(maxDelaySeconds, initialDelaySeconds * multiplier);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);

        Boolean updated = transactionTemplate.execute(status ->
                outboxEventMapper.markFailed(
                        event.getId(), event.getLockedAt(), error, nextRetryAt,
                        properties.getOutbox().getMaxPublishRetries()) == 1);
        return Boolean.TRUE.equals(updated);
    }

    private String concise(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getSimpleName();
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }
}
