-- RocketMQ 可靠消息学习模块的 PostgreSQL 表结构。
--
-- 这是学习项目，脚本只描述当前版本需要的最终结构，不承担历史版本迁移职责：
-- 1. 每次执行都会删除并重建本模块的 5 张表，表内学习数据也会一起清空；
-- 2. Outbox 直接使用当前 RocketMQ 所需的 Topic、Tag、Key 和消息组字段；
-- 3. 不使用 FOREIGN KEY，表之间只通过业务 ID 建立逻辑关联；
-- 4. 如果以后结构发生变化，直接同步修改下面的最新建表定义。

DROP TABLE IF EXISTS
    mq_transaction_record,
    mq_notification_log,
    mq_order_statistics,
    mq_consumed_message,
    mq_outbox_event;

-- ============================================================
-- 1. Outbox 本地消息表
-- ============================================================
-- 业务事务先把待发送事件写入本表，后台发布器再领取并发送到 RocketMQ。
-- 这样即使应用在数据库提交后、消息发送前宕机，事件仍然可以被后续轮询重新发送。
CREATE TABLE mq_outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    topic_name VARCHAR(200) NOT NULL,
    message_tag VARCHAR(100) NOT NULL,
    message_key VARCHAR(200) NOT NULL,
    message_group VARCHAR(200),
    deliver_at TIMESTAMP,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    CONSTRAINT ck_mq_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'FAILED', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_mq_outbox_retry_count CHECK (retry_count >= 0)
);

COMMENT ON TABLE mq_outbox_event IS
    'RocketMQ Outbox 本地消息表：保存与业务事务一起落库、等待后台发布器可靠投递的事件。';
COMMENT ON COLUMN mq_outbox_event.id IS
    'Outbox 事件主键，使用 UUID 字符串；同一次发送重试必须始终使用同一个 ID。';
COMMENT ON COLUMN mq_outbox_event.aggregate_type IS
    '聚合根类型，例如 ORDER；用于说明这条事件属于哪一类业务对象。';
COMMENT ON COLUMN mq_outbox_event.aggregate_id IS
    '聚合根业务 ID，例如订单 ID；它是逻辑关联字段，不使用数据库外键。';
COMMENT ON COLUMN mq_outbox_event.event_type IS
    '事件类型，例如 OrderCreated、OrderPaid；消费者据此选择具体业务处理逻辑。';
COMMENT ON COLUMN mq_outbox_event.schema_version IS
    '消息体结构版本；消息字段升级后，消费者可根据版本兼容不同格式。';
COMMENT ON COLUMN mq_outbox_event.topic_name IS
    'RocketMQ Topic 名称，是消息的一级分类，例如 pg_learning_order_event_topic。';
COMMENT ON COLUMN mq_outbox_event.message_tag IS
    'RocketMQ Tag，是 Topic 内的二级分类，消费者可使用 Tag 表达式过滤消息。';
COMMENT ON COLUMN mq_outbox_event.message_key IS
    'RocketMQ 消息 Key，通常使用订单号或事件 ID，便于在控制台按业务标识查询消息。';
COMMENT ON COLUMN mq_outbox_event.message_group IS
    'FIFO 顺序消息的消息组；同一组内按发送顺序消费，普通消息和延迟消息可以为空。';
COMMENT ON COLUMN mq_outbox_event.deliver_at IS
    '期望投递时间；延迟消息使用，发布器根据该时间计算剩余延迟，普通消息可以为空。';
COMMENT ON COLUMN mq_outbox_event.payload IS
    'JSONB 格式的消息正文，保存消费者处理事件所需要的业务快照。';
COMMENT ON COLUMN mq_outbox_event.status IS
    '发布状态：PENDING待发送、PROCESSING发送中、FAILED待重试、PUBLISHED已发送、DEAD终止重试。';
COMMENT ON COLUMN mq_outbox_event.retry_count IS
    '已经发生的发送失败次数；用于计算退避时间以及判断是否进入 DEAD 状态。';
COMMENT ON COLUMN mq_outbox_event.next_retry_at IS
    '下一次允许发布器领取该事件的时间；失败后向未来推迟可避免高频空转重试。';
COMMENT ON COLUMN mq_outbox_event.locked_at IS
    '发布器领取事件并标记 PROCESSING 的时间；超时后其他实例可以重新抢占，处理宕机遗留任务。';
COMMENT ON COLUMN mq_outbox_event.last_error IS
    '最近一次发送失败的错误摘要，主要用于排查问题，不应保存超长堆栈或敏感信息。';
COMMENT ON COLUMN mq_outbox_event.created_at IS
    'Outbox 事件创建时间，也就是业务事务把事件写入数据库的时间。';
COMMENT ON COLUMN mq_outbox_event.published_at IS
    '消息成功发送到 RocketMQ 并获得发送结果的时间；尚未成功发送时为空。';

-- 发布器按“状态 + 下次重试时间”领取消息，索引避免每轮调度扫描整张表。
CREATE INDEX idx_mq_outbox_publishable
    ON mq_outbox_event (status, next_retry_at, created_at);

-- 便于按某个业务聚合查询完整的事件历史。
CREATE INDEX idx_mq_outbox_aggregate
    ON mq_outbox_event (aggregate_type, aggregate_id, created_at);

-- ============================================================
-- 2. 消费幂等记录表
-- ============================================================
-- RocketMQ 可靠投递通常是“至少一次”，同一条消息可能被重复投递。
-- 消费者在执行真正业务前插入本表，唯一约束冲突表示该消费者已经成功处理过该消息。
CREATE TABLE mq_consumed_message (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100),
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mq_consumer_message UNIQUE (consumer_name, message_id)
);

COMMENT ON TABLE mq_consumed_message IS
    'RocketMQ 消费幂等表：记录每个消费者已经成功处理的消息，阻止重复投递造成重复业务操作。';
COMMENT ON COLUMN mq_consumed_message.id IS
    '数据库自增主键，仅用于本表记录定位，不作为 RocketMQ 消息 ID。';
COMMENT ON COLUMN mq_consumed_message.consumer_name IS
    '消费者业务名称；同一条消息允许被不同消费者各处理一次。';
COMMENT ON COLUMN mq_consumed_message.message_id IS
    '消息唯一 ID，通常对应 Outbox 事件 ID；与 consumer_name 组合后保证消费幂等。';
COMMENT ON COLUMN mq_consumed_message.event_type IS
    '已消费事件的类型，便于审计某类事件的处理记录。';
COMMENT ON COLUMN mq_consumed_message.aggregate_id IS
    '事件关联的业务聚合 ID，例如订单 ID；仅做逻辑关联，不使用数据库外键。';
COMMENT ON COLUMN mq_consumed_message.consumed_at IS
    '消费者完成业务处理并写入幂等记录的时间。';

CREATE INDEX idx_mq_consumed_time
    ON mq_consumed_message (consumed_at DESC, id DESC);

-- ============================================================
-- 3. 订单事件统计表
-- ============================================================
-- 学习案例只维护一行全局统计，固定主键 1 便于消费者使用 UPSERT 原子累加。
CREATE TABLE mq_order_statistics (
    id SMALLINT PRIMARY KEY,
    created_count BIGINT NOT NULL DEFAULT 0,
    paid_count BIGINT NOT NULL DEFAULT 0,
    cancelled_count BIGINT NOT NULL DEFAULT 0,
    created_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    last_event_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mq_order_statistics_singleton CHECK (id = 1)
);

COMMENT ON TABLE mq_order_statistics IS
    'RocketMQ 订单事件聚合统计表：由消费者根据订单消息原子累加学习用统计数据。';
COMMENT ON COLUMN mq_order_statistics.id IS
    '固定为 1 的统计记录主键；CHECK 约束保证学习案例只有一行全局统计。';
COMMENT ON COLUMN mq_order_statistics.created_count IS
    '已消费的订单创建事件数量。';
COMMENT ON COLUMN mq_order_statistics.paid_count IS
    '已消费的订单支付成功事件数量。';
COMMENT ON COLUMN mq_order_statistics.cancelled_count IS
    '已消费的订单取消或关闭事件数量。';
COMMENT ON COLUMN mq_order_statistics.created_amount IS
    '订单创建事件中的金额累计值，使用定点小数避免浮点精度误差。';
COMMENT ON COLUMN mq_order_statistics.last_event_at IS
    '最近一次参与统计的业务事件发生时间；还没有处理事件时为空。';
COMMENT ON COLUMN mq_order_statistics.updated_at IS
    '本行统计数据最近一次被消费者更新的时间。';

-- ============================================================
-- 4. 通知消费结果表
-- ============================================================
-- 用数据库记录模拟短信、邮件等通知结果；同一消息在同一渠道只允许成功落一条记录。
CREATE TABLE mq_notification_log (
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

COMMENT ON TABLE mq_notification_log IS
    'RocketMQ 通知消费日志表：记录消费者模拟发送短信、邮件等通知的结果，并提供本地幂等保护。';
COMMENT ON COLUMN mq_notification_log.id IS
    '数据库自增主键，用于定位一条通知日志。';
COMMENT ON COLUMN mq_notification_log.message_id IS
    '触发本次通知的消息 ID；与 channel 组合后保证同一渠道不会重复通知。';
COMMENT ON COLUMN mq_notification_log.order_id IS
    '通知关联的订单 ID；允许为空且只做逻辑关联，不使用数据库外键。';
COMMENT ON COLUMN mq_notification_log.event_type IS
    '触发通知的事件类型，例如 OrderCreated 或 OrderPaid。';
COMMENT ON COLUMN mq_notification_log.channel IS
    '通知渠道，例如 SMS、EMAIL；学习案例只记录模拟结果，不调用真实服务商。';
COMMENT ON COLUMN mq_notification_log.status IS
    '通知处理结果状态，例如 SUCCESS 或 FAILED。';
COMMENT ON COLUMN mq_notification_log.content IS
    '发送给用户的通知内容快照，便于学习和审计消息消费结果。';
COMMENT ON COLUMN mq_notification_log.created_at IS
    '通知日志创建时间，也就是消费者完成本次通知处理的时间。';

CREATE INDEX idx_mq_notification_order
    ON mq_notification_log (order_id, created_at DESC);

-- ============================================================
-- 5. RocketMQ 事务消息本地事务记录表
-- ============================================================
-- 发送半消息前先保存 PREPARED，执行订单本地事务成功后改为 COMMITTED，失败改为 ROLLED_BACK。
-- Broker 事务回查只依赖本表中的持久状态，不依赖已经消失的 HTTP 请求或 JVM 内存。
CREATE TABLE mq_transaction_record (
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

COMMENT ON TABLE mq_transaction_record IS
    'RocketMQ 事务消息本地记录表：保存半消息对应的本地事务状态，供事务监听器和 Broker 回查判断提交或回滚。';
COMMENT ON COLUMN mq_transaction_record.transaction_id IS
    '事务消息本地事务 ID，使用 UUID 字符串，也是事务回查定位本地记录的主键。';
COMMENT ON COLUMN mq_transaction_record.business_key IS
    '业务幂等键，例如订单号；有效事务中只允许存在一条相同业务键记录。';
COMMENT ON COLUMN mq_transaction_record.request_payload IS
    'JSONB 格式的原始业务请求快照，供本地事务执行、异常排查或事务回查使用。';
COMMENT ON COLUMN mq_transaction_record.status IS
    '本地事务状态：PREPARED待执行、COMMITTED已提交、ROLLED_BACK已回滚。';
COMMENT ON COLUMN mq_transaction_record.order_id IS
    '本地事务成功后生成的订单 ID；事务未成功时可以为空，且不使用数据库外键。';
COMMENT ON COLUMN mq_transaction_record.last_error IS
    '本地事务最近一次失败的错误摘要，便于排查，不应保存敏感信息。';
COMMENT ON COLUMN mq_transaction_record.created_at IS
    '事务记录创建时间，通常早于或接近半消息发送时间。';
COMMENT ON COLUMN mq_transaction_record.updated_at IS
    '事务状态或错误信息最近一次发生变化的时间。';

-- 只有 PREPARED、COMMITTED 事务会占用业务键；已回滚记录不阻止用户重新提交同一业务请求。
CREATE UNIQUE INDEX uk_mq_transaction_active_business_key
    ON mq_transaction_record (business_key)
    WHERE status IN ('PREPARED', 'COMMITTED');

CREATE INDEX idx_mq_transaction_status_created
    ON mq_transaction_record (status, created_at DESC);
