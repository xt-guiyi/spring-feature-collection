-- RabbitMQ 可靠消息学习模块的 PostgreSQL 表。
-- 本脚本全部使用 IF NOT EXISTS，不删除 orders、products 等现有学习数据，可以重复执行。

CREATE TABLE IF NOT EXISTS mq_outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    exchange_name VARCHAR(200) NOT NULL,
    routing_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    CONSTRAINT ck_mq_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_mq_outbox_retry_count CHECK (retry_count >= 0)
);

-- 发布器最常用的条件是“状态 + 下次重试时间”，索引避免每轮调度都扫描整张表。
CREATE INDEX IF NOT EXISTS idx_mq_outbox_publishable
    ON mq_outbox_event (status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_mq_outbox_aggregate
    ON mq_outbox_event (aggregate_type, aggregate_id, created_at);

CREATE TABLE IF NOT EXISTS mq_consumed_message (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100),
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mq_consumer_message UNIQUE (consumer_name, message_id)
);

CREATE INDEX IF NOT EXISTS idx_mq_consumed_time
    ON mq_consumed_message (consumed_at DESC, id DESC);

-- 学习案例只维护一行全局统计，固定主键 1 让 UPSERT 可以原子累加。
CREATE TABLE IF NOT EXISTS mq_order_statistics (
    id SMALLINT PRIMARY KEY,
    created_count BIGINT NOT NULL DEFAULT 0,
    paid_count BIGINT NOT NULL DEFAULT 0,
    cancelled_count BIGINT NOT NULL DEFAULT 0,
    created_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    last_event_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mq_order_statistics_singleton CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS mq_notification_log (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    order_id BIGINT,
    event_type VARCHAR(100) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mq_notification_message_channel UNIQUE (message_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_mq_notification_order
    ON mq_notification_log (order_id, created_at DESC);
