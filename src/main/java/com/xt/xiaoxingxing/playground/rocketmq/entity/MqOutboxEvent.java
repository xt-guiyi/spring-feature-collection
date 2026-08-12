package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 的一行待发布事件。
 *
 * <p>订单事实和本行必须在同一个 PostgreSQL 本地事务内写入；提交后由独立发布器领取并投递。
 * 因此本实体的 {@code PUBLISHED} 只表示发布器已得到 RocketMQ 发送成功结果，不等于每个消费者
 * 都已经完成业务处理。发布成功后、更新本行之前进程崩溃仍可能造成重复投递，消费者的唯一约束负责兜底。</p>
 */
@Data
public class MqOutboxEvent {

    /** 应用预先生成的稳定消息 ID；重试时不变，便于消费者去重和人工追踪。 */
    private String id;
    /** 业务聚合类型，例如 ORDER。 */
    private String aggregateType;
    /** 聚合主键的字符串形式，避免把数据库主键类型泄漏给消息协议。 */
    private String aggregateId;
    /** 领域事件类型，例如 ORDER_CREATED。 */
    private String eventType;
    /** 消息信封版本，不是数据库乐观锁版本，也不是订单状态。 */
    private Integer schemaVersion;
    /** RocketMQ Topic：一类消息的一级分类，例如订单领域事件。 */
    private String topicName;
    /** RocketMQ Tag：Topic 内的二级过滤标识。 */
    private String messageTag;
    /** RocketMQ Key：供 Broker/Dashboard 查询的稳定业务检索键。 */
    private String messageKey;
    /** FIFO 消息组；普通或延迟消息可为空。 */
    private String messageGroup;
    /** 期望投递时间；延迟 Topic 发布时由发布器据此计算剩余延迟。 */
    private LocalDateTime deliverAt;
    /** 完整版本化消息信封的 JSON 文本，Mapper 写入时显式转换为 JSONB。 */
    private String payload;
    /** PENDING/PROCESSING/FAILED/PUBLISHED/DEAD 发布状态。 */
    private String status;
    /** 发布失败次数；由 SQL 原子递增，不能在 Java 中读改写。 */
    private Integer retryCount;
    /** FAILED 事件下一次可领取的时间。 */
    private LocalDateTime nextRetryAt;
    /** 领取发布任务的短期租约时间，用于回收崩溃进程遗留的 PROCESSING 行。 */
    private LocalDateTime lockedAt;
    /** 最近一次失败的精简诊断信息，不存放完整堆栈。 */
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
