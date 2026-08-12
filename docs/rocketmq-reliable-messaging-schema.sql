-- RocketMQ 可靠消息学习模块的 PostgreSQL 表。
--
-- 本脚本可重复执行，不删除 orders、products 等既有学习数据。
-- 这里只定义 RocketMQ 学习模块当前使用的表结构，不再包含其他消息中间件的迁移逻辑。

CREATE TABLE IF NOT EXISTS mq_outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    -- Topic 是 RocketMQ 的一级消息分类，例如订单事件 Topic。
    topic_name VARCHAR(200) NOT NULL,
    -- Tag 是 Topic 内的二级过滤条件；消费者可通过 Tag 表达式订阅。
    message_tag VARCHAR(100) NOT NULL,
    -- Key 是业务查询索引，重试时必须保持不变。
    message_key VARCHAR(200) NOT NULL,
    -- 仅 FIFO 消息需要同组顺序；普通/延迟消息可为空。
    message_group VARCHAR(200),
    -- 延迟消息的期望投递时刻，由发布器计算还剩多少延迟。
    deliver_at TIMESTAMP,
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

-- 发布器按“状态 + 下次重试时间”领取，索引避免每轮调度扫描整表。
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
    -- 至少一次投递的最终并发兜底；插入冲突即代表已经成功处理。
    CONSTRAINT uk_mq_consumer_message UNIQUE (consumer_name, message_id)
);

CREATE INDEX IF NOT EXISTS idx_mq_consumed_time
    ON mq_consumed_message (consumed_at DESC, id DESC);

-- 学习案例只维护一行全局统计；固定主键 1 让 UPSERT 能原子累加。
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
    -- 本地通知记录幂等；外部通知提供商仍应支持同一个幂等键。
    CONSTRAINT uk_mq_notification_message_channel UNIQUE (message_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_mq_notification_order
    ON mq_notification_log (order_id, created_at DESC);

--
-- RocketMQ 半消息的事务回查记录。发送半消息前写 PREPARED；订单本地事务成功时改为 COMMITTED。
-- Broker 回查只依赖本表持久事实，不依赖已经丢失的 HTTP 请求或 JVM 内存；过期 PREPARED
-- 还必须通过条件更新先抢占 ROLLED_BACK，不能仅根据旧快照向 Broker 返回回滚。
--
CREATE TABLE IF NOT EXISTS mq_transaction_record (
    transaction_id VARCHAR(36) PRIMARY KEY,
    business_key VARCHAR(100) NOT NULL,
    request_payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    order_id BIGINT,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mq_transaction_status
        CHECK (status IN ('PREPARED', 'COMMITTED', 'ROLLED_BACK'))
);

--
-- 旧版表把 business_key 定义为全局 UNIQUE。CREATE TABLE IF NOT EXISTS 不会修改已有约束，
-- 所以必须显式迁移，否则 ROLLED_BACK 孤儿仍会永久占用 orderNo。
--
-- 迁移安全性：删除旧约束和创建新部分唯一索引同处一个 DO 事务块。如果异常数据
-- 使新索引创建失败，整个块回滚，不会留下“两种唯一保护都没有”的中间状态。
-- 通过系统目录按“单列 business_key 唯一约束”识别旧约束，不依赖 PostgreSQL 自动生成的约束名。
-- 重复执行时旧约束已不存在，CREATE UNIQUE INDEX IF NOT EXISTS 也会安全跳过。
--
DO $$
DECLARE
    target_schema TEXT;
    target_table TEXT;
    old_constraint RECORD;
BEGIN
    SELECT namespace.nspname, relation.relname
    INTO target_schema, target_table
    FROM pg_class relation
    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
    WHERE relation.oid = to_regclass('mq_transaction_record');

    FOR old_constraint IN
        SELECT constraint_info.conname
        FROM pg_constraint constraint_info
        JOIN pg_attribute column_info
          ON column_info.attrelid = constraint_info.conrelid
         AND column_info.attnum = constraint_info.conkey[1]
        WHERE constraint_info.conrelid = to_regclass('mq_transaction_record')
          AND constraint_info.contype = 'u'
          AND cardinality(constraint_info.conkey) = 1
          AND column_info.attname = 'business_key'
    LOOP
        EXECUTE format(
                'ALTER TABLE %I.%I DROP CONSTRAINT %I',
                target_schema, target_table, old_constraint.conname);
    END LOOP;

    EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS uk_mq_transaction_active_business_key ON %I.%I '
            '(business_key) WHERE status IN (''PREPARED'', ''COMMITTED'')',
            target_schema, target_table);
END $$;

CREATE INDEX IF NOT EXISTS idx_mq_transaction_status_created
    ON mq_transaction_record (status, created_at DESC);
