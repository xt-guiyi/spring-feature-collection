package com.xt.xiaoxingxing.playground.rocketmq.vo;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOutboxEvent;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Outbox 运维查询响应。
 *
 * <p>接口层不直接返回 {@link MqOutboxEvent}：数据库实体可能随着索引、锁租约或迁移策略变化，
 * 而 HTTP 契约应由独立 VO 明确控制。学习时可以对照本类理解“持久化模型”和“对外模型”是两层职责。</p>
 */
@Data
public class RocketOutboxEventVO {

    private String id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Integer schemaVersion;
    private String topicName;
    private String messageTag;
    private String messageKey;
    private String messageGroup;
    private LocalDateTime deliverAt;
    /** 完整版本化信封，便于学习环境排查；正式系统可按权限隐藏或脱敏。 */
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    /** 组装发生在 Service 边界，Controller 不再知道数据库实体。 */
    public static RocketOutboxEventVO from(MqOutboxEvent source) {
        if (source == null) {
            return null;
        }
        RocketOutboxEventVO target = new RocketOutboxEventVO();
        target.setId(source.getId());
        target.setAggregateType(source.getAggregateType());
        target.setAggregateId(source.getAggregateId());
        target.setEventType(source.getEventType());
        target.setSchemaVersion(source.getSchemaVersion());
        target.setTopicName(source.getTopicName());
        target.setMessageTag(source.getMessageTag());
        target.setMessageKey(source.getMessageKey());
        target.setMessageGroup(source.getMessageGroup());
        target.setDeliverAt(source.getDeliverAt());
        target.setPayload(source.getPayload());
        target.setStatus(source.getStatus());
        target.setRetryCount(source.getRetryCount());
        target.setNextRetryAt(source.getNextRetryAt());
        target.setLockedAt(source.getLockedAt());
        target.setLastError(source.getLastError());
        target.setCreatedAt(source.getCreatedAt());
        target.setPublishedAt(source.getPublishedAt());
        return target;
    }
}
