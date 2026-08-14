package com.xt.xiaoxingxing.playground.rocketmq.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.service.OutboxEventService;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessagePublisher;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketPublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 从 PostgreSQL 领取 Outbox，按 Topic 类型可靠发布并推进状态。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxPublishScheduler {

    private final OutboxEventService outboxEventService;
    private final RocketMessageCodec messageCodec;
    private final RocketMessagePublisher publisher;
    private final RocketMqLearningProperties properties;
    private final AtomicBoolean publishing = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${playground.rocketmq.outbox.fixed-delay-millis}")
    public void publishPendingEvents() {
        if (!publishing.compareAndSet(false, true)) {
            log.debug("上一轮Outbox仍在发布，本轮跳过");
            return;
        }
        try {
            List<MqOutboxEvent> events = outboxEventService.claimPublishable();
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
     * 实现步骤：
     * 第1步，从 JSONB 恢复原始信封；
     * 第2步，按 NORMAL/DELAY Topic 选择发布 API；
     * 第3步，Broker 接收成功后使用领取时的 lockedAt 租约令牌标记 PUBLISHED；
     * 第4步，失败则携带同一令牌记录退避时间。
     *
     * <p>Broker 已接收、数据库尚未标记时进程崩溃会导致再次发布，所以只能保证至少一次。
     * 消费端必须使用稳定业务 messageId 幂等。若租约已过期，旧 worker 不得覆盖新 worker 的状态。</p>
     */
    private void publishOne(MqOutboxEvent event) {
        try {
            // 第1步：恢复信封时也校验协议版本，坏消息有限失败后进入 DEAD，避免静默丢弃。
            RocketMessageEnvelope<JsonNode> envelope = messageCodec.fromJson(event.getPayload());

            // 第2步：DELAY Topic 计算剩余延迟；最低值来自 YAML，避免过期事件误走普通发送 API。
            RocketPublishResult result;
            if (properties.getTopics().getDelay().equals(event.getTopicName())) {
                long minimumDelayMillis = properties.getOutbox().getMinimumBrokerDelayMillis();
                long millis = event.getDeliverAt() == null ? minimumDelayMillis
                        : Math.max(minimumDelayMillis,
                                ChronoUnit.MILLIS.between(LocalDateTime.now(), event.getDeliverAt()));
                result = publisher.publishDelay(event.getTopicName(), event.getMessageTag(), event.getMessageKey(),
                        Duration.ofMillis(millis), envelope);
            } else if (properties.getTopics().getNormal().equals(event.getTopicName())) {
                result = publisher.publishNormal(
                        event.getTopicName(), event.getMessageTag(), event.getMessageKey(), envelope);
            } else {
                throw new IllegalArgumentException("Outbox不支持的Topic类型: " + event.getTopicName());
            }

            // 第3步：发送成功不等于状态一定能覆盖；0 行表示租约过期，由新 worker 继续负责。
            if (result.isSuccess()) {
                if (outboxEventService.markPublished(event)) {
                    log.info("Outbox发布成功: eventId={}, brokerMessageId={}, topic={}, tag={}",
                            event.getId(), result.getBrokerMessageId(), event.getTopicName(), event.getMessageTag());
                } else {
                    log.warn("Outbox已被Broker接收但领取租约已过期，不覆盖新worker状态: eventId={}", event.getId());
                }
                return;
            }

            // 第4步：只允许仍持有同一 lockedAt 的 worker 记录失败；否则让新的领取者决定最终状态。
            if (!outboxEventService.markFailed(event, result.getReason())) {
                log.warn("Outbox发布失败但领取租约已过期，不覆盖新worker状态: eventId={}", event.getId());
            }
        } catch (Exception exception) {
            try {
                if (!outboxEventService.markFailed(event, concise(exception))) {
                    log.warn("Outbox异常但领取租约已过期，不覆盖新worker状态: eventId={}", event.getId());
                }
            } catch (Exception stateException) {
                log.error("Outbox异常且失败状态也无法保存: eventId={}", event.getId(), stateException);
            }
            log.error("Outbox事件处理异常: eventId={}", event.getId(), exception);
        }
    }

    private String concise(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
