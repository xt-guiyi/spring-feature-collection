package com.xt.xiaoxingxing.playground.rabbitmq.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import com.xt.xiaoxingxing.playground.rabbitmq.service.OutboxEventService;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitMessageCodec;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitPublishResult;
import com.xt.xiaoxingxing.playground.rabbitmq.support.ReliableRabbitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transactional Outbox 定时发布器。
 *
 * <p>它负责解决“数据库已提交，但应用在调用 RabbitMQ 前崩溃”的问题。Outbox 行一直保留在 PostgreSQL，
 * 调度器恢复后仍能继续发送。它不能消除重复消息，因此消费者必须继续做幂等。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublishScheduler {

    private final OutboxEventService outboxEventService;
    private final RabbitMessageCodec messageCodec;
    private final ReliableRabbitPublisher publisher;

    /** 单实例内防止上一次批次尚未完成时，调度线程又启动一轮。 */
    private final AtomicBoolean publishing = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${playground.rabbitmq.outbox.fixed-delay-millis:3000}")
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
        } catch (Exception e) {
            // 捕获调度批次异常，防止某次数据库短暂故障永久停止后续调度。
            log.error("Outbox批量认领或发布发生异常", e);
        } finally {
            publishing.set(false);
        }
    }

    private void publishOne(MqOutboxEvent event) {
        /*
         * 完整步骤：
         * 第1步：从 JSONB 还原消息信封；
         * 第2步：向 Outbox 记录的 exchange/routingKey 发布并等待 Confirm；
         * 第3步：ACK 且无 Return 才把数据库状态改为 PUBLISHED；
         * 第4步：任何失败都保存原因和下一次退避时间。
         *
         * 如果第3步之前进程崩溃，Broker 可能已经保存消息，但数据库仍是 PROCESSING。
         * 锁超时后会再次发送，所以这是“至少一次”而不是“恰好一次”。
         */
        try {
            RabbitMessageEnvelope<JsonNode> envelope = messageCodec.fromJson(event.getPayload());
            RabbitPublishResult result = publisher.publishAndWait(
                    event.getExchangeName(), event.getRoutingKey(), envelope);

            if (result.isSuccess()) {
                outboxEventService.markPublished(event.getId());
                log.info("Outbox发布成功: eventId={}, eventType={}, routingKey={}",
                        event.getId(), event.getEventType(), event.getRoutingKey());
            } else {
                outboxEventService.markFailed(event, result.getReason());
                log.warn("Outbox发布失败，等待重试: eventId={}, reason={}", event.getId(), result.getReason());
            }
        } catch (Exception e) {
            outboxEventService.markFailed(event, conciseMessage(e));
            log.error("Outbox事件处理异常: eventId={}", event.getId(), e);
        }
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
