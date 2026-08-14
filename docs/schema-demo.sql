-- 1. 全量重建当前学习项目结构
-- 注意：这不是生产环境的增量迁移脚本。执行后会先删除当前案例表及其数据，再按照最新结构重新创建。
-- 表之间使用逻辑关联 ID，不定义 FOREIGN KEY，因此按依赖顺序删除即可，无需使用 CASCADE。
DROP TABLE IF EXISTS mq_notification_log;
DROP TABLE IF EXISTS mq_order_statistics;
DROP TABLE IF EXISTS mq_consumed_message;
DROP TABLE IF EXISTS mq_transaction_record;
DROP TABLE IF EXISTS mq_outbox_event;
DROP TABLE IF EXISTS order_products;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS id_cards;
DROP TABLE IF EXISTS product_profiles;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;

-- 2. 创建新表（逻辑外键，不带 FOREIGN KEY 约束）

-- 用户表
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    -- status 由数据库提供统一默认值，使普通 MyBatis 与 MyBatis-Plus 在未传状态时都得到 ACTIVE。
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE users IS '用户主数据表：保存 PostgreSQL 学习案例中用户的稳定基础信息，订单、身份证、问卷等业务通过 user_id 进行逻辑关联，不使用数据库外键。';
COMMENT ON COLUMN users.id IS '用户主键：由 BIGSERIAL 对应的数据库序列生成，仅用于数据库内部唯一标识。';
COMMENT ON COLUMN users.username IS '登录或展示用户名：当前案例要求非空，但没有唯一约束，是否允许重名由具体业务规则决定。';
COMMENT ON COLUMN users.email IS '用户邮箱：可为空，案例中用于联系方式和模糊查询，不承担用户唯一身份保证。';
COMMENT ON COLUMN users.phone IS '用户手机号：可为空，可用于演示 IS NULL 等动态条件查询。';
COMMENT ON COLUMN users.status IS '用户状态：默认 ACTIVE 且不允许为空；跨模块写入前可据此判断用户是否允许参与业务。';
COMMENT ON COLUMN users.created_at IS '用户创建时间：插入时默认使用数据库当前时间，用于排序、审计和时间范围查询。';

-- 身份证表（与用户一对一）
CREATE TABLE id_cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    card_number VARCHAR(18) NOT NULL,
    real_name VARCHAR(50)
);

COMMENT ON TABLE id_cards IS '用户身份证信息表：通过 user_id 与 users 形成逻辑一对一关系；不使用外键，用户存在性由 Service 校验。';
COMMENT ON COLUMN id_cards.id IS '身份证记录主键：由数据库序列自动生成。';
COMMENT ON COLUMN id_cards.user_id IS '关联用户 ID：UNIQUE 保证一个用户最多只有一条身份证记录，但不会自动验证 users 表中是否存在该用户。';
COMMENT ON COLUMN id_cards.card_number IS '身份证号码：案例要求非空；真实系统应加密或脱敏存储，并严格控制查询与日志输出。';
COMMENT ON COLUMN id_cards.real_name IS '用户真实姓名：可为空；真实系统向前端返回时通常需要脱敏和权限控制。';

-- 订单表（与用户一对多）
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    total_amount DECIMAL(10, 2),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Service 已经拒绝负金额，CHECK 再防止脚本或其他入口绕过 Java 直接写入非法数据。
    CONSTRAINT ck_orders_total_amount_non_negative
        CHECK (total_amount IS NULL OR total_amount >= 0)
);

COMMENT ON TABLE orders IS '订单主表：保存订单级别的用户归属、业务单号、总金额和状态；user_id 是逻辑关联字段，不定义外键。';
COMMENT ON COLUMN orders.id IS '订单主键：由数据库序列自动生成，供订单明细和消息记录进行逻辑关联。';
COMMENT ON COLUMN orders.user_id IS '下单用户 ID：逻辑关联 users.id；没有外键时，Service 必须校验用户存在并处理用户被删除后的历史数据。';
COMMENT ON COLUMN orders.order_no IS '业务订单号：非空且唯一，可作为接口幂等键和面向业务人员查询订单的稳定标识。';
COMMENT ON COLUMN orders.total_amount IS '订单总金额快照：精确到两位小数且不能为负；它记录下单时金额，不应随商品当前价格变化。';
COMMENT ON COLUMN orders.status IS '订单状态：例如 PENDING、PAID、CANCELLED；案例暂未限定取值集合，状态流转需由业务层校验。';
COMMENT ON COLUMN orders.created_at IS '订单创建时间：默认使用数据库当前时间，是用户订单分页、最新订单和时间统计的排序依据。';

-- 用户订单查询、LATERAL 最新订单和按时间分页分别使用下面两个索引。
CREATE INDEX idx_orders_user_created
    ON orders (user_id, created_at DESC, id DESC);
CREATE INDEX idx_orders_created
    ON orders (created_at DESC, id DESC);

-- 商品表
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT,
    CONSTRAINT ck_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_products_stock_non_negative CHECK (stock IS NULL OR stock >= 0)
);

COMMENT ON TABLE products IS '商品主表：保存商品名称、当前销售价格和当前库存等稳定关系型字段。';
COMMENT ON COLUMN products.id IS '商品主键：由数据库序列自动生成。';
COMMENT ON COLUMN products.name IS '商品名称：非空但允许重名；商品的业务唯一性不能只依赖展示名称。';
COMMENT ON COLUMN products.price IS '商品当前价格：精确到两位小数且不能为负；历史成交价应读取 order_products.unit_price 快照。';
COMMENT ON COLUMN products.stock IS '商品当前库存：可为空且不能为负；并发扣减时应使用带 stock >= quantity 条件的原子 UPDATE。';

-- 商品动态扩展信息：稳定的价格、库存仍使用普通列，只有不同品类结构不一致的规格放入 JSONB。
-- product_id 继续遵循本 playground 的“逻辑外键”约定，不增加数据库 FOREIGN KEY。
CREATE TABLE product_profiles (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_profile_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object')
);

COMMENT ON TABLE product_profiles IS '商品动态扩展信息表：使用 JSONB 保存不同品类不一致的属性，固定且经常查询的核心字段仍应放在 products 普通列中。';
COMMENT ON COLUMN product_profiles.id IS '商品扩展记录主键：由数据库序列自动生成。';
COMMENT ON COLUMN product_profiles.product_id IS '商品 ID：与 products.id 逻辑一对一关联；UNIQUE 防止同一商品出现多份扩展文档，但不验证商品一定存在。';
COMMENT ON COLUMN product_profiles.attributes IS '动态商品属性：必须是 JSON 对象，默认空对象；可保存字符串、数值、布尔值、数组和嵌套对象。';
COMMENT ON COLUMN product_profiles.created_at IS '扩展记录创建时间：插入时由数据库自动填写，用于审计。';
COMMENT ON COLUMN product_profiles.updated_at IS '扩展记录最后更新时间：默认只负责初始值，执行更新时仍需由 SQL 或业务代码显式刷新。';

-- PostgreSQL 可以直接对完整 JSONB 建 GIN 索引，为 @>、? 等受支持操作符提供索引能力。
-- 这条 USING GIN 语法不能原样用于 MySQL；MySQL InnoDB 可使用函数索引、生成列索引，
-- 对 JSON 数组还可以使用多值索引。区别是索引模型不同，不等于 MySQL 完全不能索引 JSON。
CREATE INDEX idx_product_profiles_attributes_gin
    ON product_profiles USING GIN (attributes);

-- 频繁按照某一个固定属性等值查询时，表达式 B-Tree 往往比“什么都交给 GIN”更直接。
-- MySQL 8.4 也支持函数索引，因此这一能力不是 PostgreSQL 独有，只是表达式语法不同。
CREATE INDEX idx_product_profiles_brand
    ON product_profiles ((attributes ->> 'brand'));

-- 订单商品关联表（订单与商品多对多）
CREATE TABLE order_products (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT uk_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_products_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_products_unit_price_non_negative CHECK (unit_price >= 0)
);

COMMENT ON TABLE order_products IS '订单商品明细表：连接 orders 与 products，保存每种商品在订单中的购买数量和成交单价快照。';
COMMENT ON COLUMN order_products.id IS '订单商品明细主键：由数据库序列自动生成。';
COMMENT ON COLUMN order_products.order_id IS '订单 ID：逻辑关联 orders.id；与 product_id 组成唯一业务组合，防止同一订单重复插入同一商品明细。';
COMMENT ON COLUMN order_products.product_id IS '商品 ID：逻辑关联 products.id；历史商品被下架或改名时，订单明细仍应保留。';
COMMENT ON COLUMN order_products.quantity IS '购买数量：必须大于 0；与 unit_price 相乘可计算该明细的成交小计。';
COMMENT ON COLUMN order_products.unit_price IS '成交单价快照：必须大于等于 0，保存下单当时价格，不能在查询历史订单时改用 products.price。';

-- RocketMQ Transactional Outbox：订单和待发送事件处于同一个 PostgreSQL 本地事务。
-- 本表只保存待发布事实；发布器采用 FOR UPDATE SKIP LOCKED 原子领取，消费者仍需幂等。
CREATE TABLE mq_outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    -- Topic 是 RocketMQ 一级消息分类，例如订单事件 Topic。
    topic_name VARCHAR(200) NOT NULL,
    -- Tag 在 Topic 内过滤消息，消费者订阅表达式基于它选择事件类型。
    message_tag VARCHAR(100) NOT NULL,
    -- 稳定业务查询键；同一次 Outbox 重试不得改变。
    message_key VARCHAR(200) NOT NULL,
    -- FIFO 消息的组键；普通和延迟消息可为空。
    message_group VARCHAR(200),
    -- 延迟消息目标投递时间，发布器计算剩余延迟后交给 RocketMQ。
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

COMMENT ON TABLE mq_outbox_event IS 'RocketMQ Outbox 事件表：业务事务先同时写订单和待发送事件，后台发布器再可靠投递，解决数据库提交成功但消息未发送的问题。';
COMMENT ON COLUMN mq_outbox_event.id IS 'Outbox 事件主键：应用生成 UUID；同一条事件重试发布时必须保持不变。';
COMMENT ON COLUMN mq_outbox_event.aggregate_type IS '聚合根类型：标识事件属于哪类业务对象，例如 ORDER，便于按业务聚合查询和排查。';
COMMENT ON COLUMN mq_outbox_event.aggregate_id IS '聚合根 ID：保存订单 ID 等业务对象标识，与 aggregate_type 共同定位事件来源。';
COMMENT ON COLUMN mq_outbox_event.event_type IS '事件类型：例如 OrderCreated；消费者据此选择处理逻辑，语义变更时应配合 schema_version 演进。';
COMMENT ON COLUMN mq_outbox_event.schema_version IS '消息结构版本：用于消费者兼容不同版本的 payload，不能把它误当作数据库乐观锁版本。';
COMMENT ON COLUMN mq_outbox_event.topic_name IS 'RocketMQ Topic 名称：一级消息分类，由发布器把事件发送到该 Topic。';
COMMENT ON COLUMN mq_outbox_event.message_tag IS 'RocketMQ Tag：Topic 内的二级业务分类，消费者可通过订阅表达式过滤事件。';
COMMENT ON COLUMN mq_outbox_event.message_key IS 'RocketMQ 消息业务键：用于控制台查询和问题追踪；同一 Outbox 事件重试时保持稳定。';
COMMENT ON COLUMN mq_outbox_event.message_group IS '顺序消息组键：同一组消息发送到同一队列以维持局部顺序；普通消息和延迟消息可以为空。';
COMMENT ON COLUMN mq_outbox_event.deliver_at IS '计划投递时间：为空表示尽快发送；非空时发布器据此计算剩余延迟，适用于订单超时等延迟消息。';
COMMENT ON COLUMN mq_outbox_event.payload IS '消息正文：JSONB 格式的业务事件快照；应包含消费者完成处理所需的数据，避免消费时反复回查生产者数据库。';
COMMENT ON COLUMN mq_outbox_event.status IS '发布状态：PENDING 待处理、PROCESSING 已领取、FAILED 可重试、PUBLISHED 已发布、DEAD 超限人工处理。';
COMMENT ON COLUMN mq_outbox_event.retry_count IS '发布重试次数：只能递增且不能为负，用于退避计算和判断是否进入 DEAD。';
COMMENT ON COLUMN mq_outbox_event.next_retry_at IS '下次允许发布时间：发布器只领取到期记录，失败后通过推迟该时间实现退避重试。';
COMMENT ON COLUMN mq_outbox_event.locked_at IS '发布任务领取时间：PROCESSING 记录长时间未完成时，可据此识别进程崩溃造成的超时占用并重新领取。';
COMMENT ON COLUMN mq_outbox_event.last_error IS '最后一次发布失败原因：用于学习和运维排查，不应写入密码、令牌等敏感信息。';
COMMENT ON COLUMN mq_outbox_event.created_at IS '事件创建时间：与业务数据在同一本地事务中落库，可用于保证发布领取顺序和追踪积压时长。';
COMMENT ON COLUMN mq_outbox_event.published_at IS '成功发布时间：只有收到 RocketMQ 成功结果并将状态更新为 PUBLISHED 时填写。';

CREATE INDEX idx_mq_outbox_publishable
    ON mq_outbox_event (status, next_retry_at, created_at);
CREATE INDEX idx_mq_outbox_aggregate
    ON mq_outbox_event (aggregate_type, aggregate_id, created_at);

-- 每个消费者用 (consumer_name, message_id) 唯一键抵挡 RocketMQ 至少一次投递产生的重复消息。
CREATE TABLE mq_consumed_message (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100),
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mq_consumer_message UNIQUE (consumer_name, message_id)
);

COMMENT ON TABLE mq_consumed_message IS 'RocketMQ 消费幂等记录表：消费者在执行业务副作用前登记消息，利用唯一约束抵挡至少一次投递产生的重复消费。';
COMMENT ON COLUMN mq_consumed_message.id IS '消费记录主键：由数据库序列自动生成。';
COMMENT ON COLUMN mq_consumed_message.consumer_name IS '消费者逻辑名称：不同业务消费者可以分别处理同一消息，因此它与 message_id 共同参与唯一约束。';
COMMENT ON COLUMN mq_consumed_message.message_id IS '消息唯一 ID：对应生产端稳定的事件标识；同一消费者重复收到该 ID 时应跳过业务副作用。';
COMMENT ON COLUMN mq_consumed_message.event_type IS '已消费事件类型：用于审计消费者处理了哪类消息。';
COMMENT ON COLUMN mq_consumed_message.aggregate_id IS '事件关联的业务对象 ID：可为空，用于按订单等聚合根定位消费记录。';
COMMENT ON COLUMN mq_consumed_message.consumed_at IS '成功登记消费的时间：用于审计、排查重复投递和按保留策略清理历史幂等记录。';

CREATE INDEX idx_mq_consumed_time
    ON mq_consumed_message (consumed_at DESC, id DESC);

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

COMMENT ON TABLE mq_order_statistics IS '订单消息统计汇总表：消费者根据订单事件增量更新的一行物化统计；它是学习投影，不是订单事实数据源。';
COMMENT ON COLUMN mq_order_statistics.id IS '单例主键：CHECK 强制只能取 1，使整张表最多维护一个全局汇总逻辑行。';
COMMENT ON COLUMN mq_order_statistics.created_count IS '已处理的订单创建事件数量：默认 0，幂等控制成功后才能累加。';
COMMENT ON COLUMN mq_order_statistics.paid_count IS '已处理的订单支付事件数量：默认 0，不能用重复消息反复累加。';
COMMENT ON COLUMN mq_order_statistics.cancelled_count IS '已处理的订单取消事件数量：默认 0，用于演示事件驱动统计。';
COMMENT ON COLUMN mq_order_statistics.created_amount IS '订单创建金额累计值：默认 0，使用 DECIMAL 避免浮点金额误差。';
COMMENT ON COLUMN mq_order_statistics.last_event_at IS '最近一次参与统计的业务事件时间：可用于判断统计投影的新鲜度。';
COMMENT ON COLUMN mq_order_statistics.updated_at IS '统计行最后更新时间：每次增量更新时需要显式刷新，默认值只负责首次创建。';

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

COMMENT ON TABLE mq_notification_log IS '消息通知模拟日志表：记录订单事件触发的短信、邮件等通知结果，并通过消息与渠道唯一组合避免重复发送。';
COMMENT ON COLUMN mq_notification_log.id IS '通知日志主键：由数据库序列自动生成。';
COMMENT ON COLUMN mq_notification_log.message_id IS '触发通知的消息 ID：与 channel 组成唯一约束，防止同一消息在同一渠道重复产生副作用。';
COMMENT ON COLUMN mq_notification_log.order_id IS '关联订单 ID：逻辑关联 orders.id，可为空以容纳无法解析订单的异常记录。';
COMMENT ON COLUMN mq_notification_log.event_type IS '触发通知的事件类型：用于解释为什么产生该通知。';
COMMENT ON COLUMN mq_notification_log.channel IS '通知渠道：例如 SMS、EMAIL；同一消息允许在不同渠道分别通知。';
COMMENT ON COLUMN mq_notification_log.status IS '通知执行状态：记录模拟发送成功或失败结果，具体合法值和流转由业务层约束。';
COMMENT ON COLUMN mq_notification_log.content IS '通知内容快照：保存当时实际准备发送的文本，便于审计；真实系统应避免写入敏感数据。';
COMMENT ON COLUMN mq_notification_log.created_at IS '通知日志创建时间：默认使用数据库当前时间，用于按订单查看通知时间线。';

CREATE INDEX idx_mq_notification_order
    ON mq_notification_log (order_id, created_at DESC);

-- RocketMQ 事务消息回查依据：半消息先插 PREPARED，本地订单事务成功时更新为 COMMITTED。
-- 回查不得相信内存状态；只有进行中/已提交记录占用 business_key，已回滚命令允许受控重试。
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

COMMENT ON TABLE mq_transaction_record IS 'RocketMQ 事务消息本地事务记录表：持久化半消息对应的业务执行状态，为 Broker 事务回查提供可信依据。';
COMMENT ON COLUMN mq_transaction_record.transaction_id IS '事务消息 ID：应用生成 UUID，用于关联半消息、本地事务执行和 Broker 回查。';
COMMENT ON COLUMN mq_transaction_record.business_key IS '业务幂等键：例如订单号；进行中或已提交记录通过部分唯一索引占用该键，防止并发重复创建。';
COMMENT ON COLUMN mq_transaction_record.request_payload IS '原始业务请求快照：JSONB 格式，用于事务执行审计和问题排查，不应存放明文密码等秘密。';
COMMENT ON COLUMN mq_transaction_record.status IS '本地事务状态：PREPARED 执行中、COMMITTED 已提交、ROLLED_BACK 已回滚；Broker 回查据此决定提交或回滚半消息。';
COMMENT ON COLUMN mq_transaction_record.order_id IS '本地事务创建的订单 ID：提交成功后填写，逻辑关联 orders.id；回滚时可以为空。';
COMMENT ON COLUMN mq_transaction_record.last_error IS '本地事务最后一次失败原因：回滚和排错使用，不应记录敏感凭据。';
COMMENT ON COLUMN mq_transaction_record.created_at IS '事务记录创建时间：首次收到事务消息时写入，用于识别长时间停留在 PREPARED 的记录。';
COMMENT ON COLUMN mq_transaction_record.updated_at IS '事务状态最后更新时间：状态变化时需要显式刷新，Broker 回查可据此辅助判断处理时效。';

-- PostgreSQL 部分唯一索引将“业务键占用权”与持久状态绑定：
-- PREPARED/COMMITTED 拒绝同 orderNo 并发重复，ROLLED_BACK 不占用键，可以使用新 transactionId 重试。
CREATE UNIQUE INDEX uk_mq_transaction_active_business_key
    ON mq_transaction_record (business_key)
    WHERE status IN ('PREPARED', 'COMMITTED');

CREATE INDEX idx_mq_transaction_status_created
    ON mq_transaction_record (status, created_at DESC);

-- 3. 插入示例数据

INSERT INTO users (username, email, phone, status) VALUES
('zhangsan', 'zhangsan@example.com', '13800138000', 'ACTIVE'),
('lisi', 'lisi@example.com', '13900139000', 'ACTIVE');

INSERT INTO id_cards (user_id, card_number, real_name) VALUES
(1, '110101199001011234', '张三'),
(2, '110101199002022345', '李四');

INSERT INTO orders (user_id, order_no, total_amount, status) VALUES
(1, 'O20240801001', 19998, 'PAID'),
(1, 'O20240801002', 5999, 'PENDING'),
(2, 'O20240801003', 12999, 'PAID');

INSERT INTO products (name, price, stock) VALUES
('iPhone 15', 5999, 100),
('MacBook Pro', 12999, 50),
('AirPods Pro', 1999, 200);

-- 三条默认 JSONB 数据覆盖字符串、数字、布尔值、数组和嵌套对象，便于直接调用学习接口。
-- PostgreSQL 的 ::jsonb 类型转换不能原样用于 MySQL；MySQL JSON 列可直接接收合法 JSON 文本。
INSERT INTO product_profiles (product_id, attributes) VALUES
(1, '{
  "brand": "Apple",
  "color": "黑色",
  "storageGb": 256,
  "network": "5G",
  "tags": ["手机", "5G", "iOS"],
  "warranty": {"enabled": true, "months": 12}
}'::jsonb),
(2, '{
  "brand": "Apple",
  "color": "深空黑色",
  "memoryGb": 18,
  "storageGb": 512,
  "chip": "M3 Pro",
  "tags": ["电脑", "macOS", "办公"],
  "warranty": {"enabled": true, "months": 24}
}'::jsonb),
(3, '{
  "brand": "Apple",
  "color": "白色",
  "noiseCancellation": true,
  "waterResistance": "IP54",
  "tags": ["耳机", "蓝牙", "降噪"],
  "warranty": {"enabled": true, "months": 12}
}'::jsonb);

INSERT INTO order_products (order_id, product_id, quantity, unit_price) VALUES
(1, 1, 2, 5999),
(1, 2, 1, 12999),
(2, 1, 1, 5999),
(3, 2, 1, 12999);

-- 4. 常用关联查询示例

-- 查询用户及其身份证（一对一）
SELECT u.id, u.username, ic.card_number, ic.real_name
FROM users u
LEFT JOIN id_cards ic ON u.id = ic.user_id;

-- 查询用户及其所有订单（一对多）
SELECT u.id, u.username, o.order_no, o.total_amount, o.status
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
ORDER BY u.id, o.created_at;

-- 查询某个订单下的所有商品（多对多）
SELECT o.order_no, p.name, op.quantity, op.unit_price,
       op.quantity * op.unit_price AS subtotal
FROM orders o
JOIN order_products op ON o.id = op.order_id
JOIN products p ON op.product_id = p.id
WHERE o.order_no = 'O20240801001';

-- 查询每个用户的订单数量和总消费金额
SELECT u.id, u.username,
       COUNT(o.id) AS order_count,
       COALESCE(SUM(o.total_amount), 0) AS total_spent
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.username;
