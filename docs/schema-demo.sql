-- 1. 全量重建当前学习项目结构
-- 注意：这不是生产环境的增量迁移脚本。执行后会先删除当前案例表及其数据，再按照最新结构重新创建。
-- 表之间使用逻辑关联 ID，不定义数据库级外键约束，因此按依赖顺序删除即可，无需使用 CASCADE。
DROP TABLE IF EXISTS order_statistics;
DROP TABLE IF EXISTS mq_consumed_message;
DROP TABLE IF EXISTS mq_transaction_record;
DROP TABLE IF EXISTS mq_outbox_event;
DROP TABLE IF EXISTS order_products;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS id_cards;
DROP TABLE IF EXISTS product_profiles;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;

-- 2. 创建新表（只保存逻辑关联 ID，不带数据库级外键约束）

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
-- product_id 继续遵循本 playground 的“逻辑关联”约定，不增加数据库级外键约束。
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

-- RocketMQ Transactional Outbox：订单事实和待发布事件处于同一个 PostgreSQL 本地事务。
-- 表中只保存发布器真正读取的字段；消息协议版本保存在 payload 信封内，不重复维护第二份列。
CREATE TABLE mq_outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic_name VARCHAR(200) NOT NULL,
    message_tag VARCHAR(100) NOT NULL,
    message_key VARCHAR(200) NOT NULL,
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

COMMENT ON TABLE mq_outbox_event IS 'RocketMQ Outbox事件表：业务事务先同时提交订单事实和消息意图，后台发布器再可靠发送。';
COMMENT ON COLUMN mq_outbox_event.id IS 'Outbox事件主键，同时是信封内稳定业务messageId；重复发布时保持不变。';
COMMENT ON COLUMN mq_outbox_event.aggregate_id IS '事件关联的业务对象ID；当前案例保存订单ID的字符串形式，不定义数据库外键。';
COMMENT ON COLUMN mq_outbox_event.event_type IS '明确业务事件类型：ORDER_CREATED、ORDER_PAID、ORDER_CANCELLED或Outbox超时检查。';
COMMENT ON COLUMN mq_outbox_event.topic_name IS 'RocketMQ Topic名称；Outbox案例只写NORMAL订单事件Topic或DELAY超时Topic。';
COMMENT ON COLUMN mq_outbox_event.message_tag IS 'RocketMQ Tag；消费者在Topic内部根据该二级业务分类过滤消息。';
COMMENT ON COLUMN mq_outbox_event.message_key IS 'RocketMQ查询Key；使用稳定订单号，便于在Dashboard按业务定位消息。';
COMMENT ON COLUMN mq_outbox_event.deliver_at IS '期望投递时间；普通事件为空，付款超时检查保存未来时间并由发布器计算剩余延迟。';
COMMENT ON COLUMN mq_outbox_event.payload IS '完整版本化JSON消息信封；包含稳定messageId、eventType、协议版本、聚合ID、时间和业务负载。';
COMMENT ON COLUMN mq_outbox_event.status IS '发布状态：PENDING待领取、PROCESSING已租赁、FAILED待重试、PUBLISHED已发送、DEAD超限。';
COMMENT ON COLUMN mq_outbox_event.retry_count IS '发布失败次数；由数据库SQL原子递增，用于退避和DEAD判断。';
COMMENT ON COLUMN mq_outbox_event.next_retry_at IS '下次允许领取时间；失败后按指数退避推迟，调度器不会提前领取。';
COMMENT ON COLUMN mq_outbox_event.locked_at IS '本次领取租约时间；成功和失败回写都必须携带相同时间，阻止过期worker覆盖新worker。';
COMMENT ON COLUMN mq_outbox_event.last_error IS '最近一次发布错误摘要；最长1000字符，不应保存密码、令牌或完整敏感请求。';
COMMENT ON COLUMN mq_outbox_event.created_at IS '事件写入时间；用于稳定领取顺序、积压诊断和审计。';
COMMENT ON COLUMN mq_outbox_event.published_at IS '获得Broker发送成功结果并成功回写PUBLISHED的时间。';

CREATE INDEX idx_mq_outbox_publishable
    ON mq_outbox_event (status, next_retry_at, created_at, id);
CREATE INDEX idx_mq_outbox_aggregate
    ON mq_outbox_event (aggregate_id, created_at DESC);

-- RocketMQ 是至少一次投递；同一消息可能再次到达同一消费组，必须以数据库唯一键最终裁决。
CREATE TABLE mq_consumed_message (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100),
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mq_consumer_message UNIQUE (consumer_name, message_id)
);

COMMENT ON TABLE mq_consumed_message IS 'RocketMQ消费幂等记录表：不同消费组分别登记同一消息，同组重复投递只能首次取得处理权。';
COMMENT ON COLUMN mq_consumed_message.id IS '消费记录主键；由PostgreSQL序列生成，仅作为数据库内部标识。';
COMMENT ON COLUMN mq_consumed_message.consumer_name IS '消费者逻辑名称；本案例使用ConsumerGroup名称，使两套方案和不同副作用拥有独立幂等维度。';
COMMENT ON COLUMN mq_consumed_message.message_id IS '应用生成的稳定业务messageId；不是Broker每次投递尝试的临时标识。';
COMMENT ON COLUMN mq_consumed_message.event_type IS '已处理的业务事件类型；用于核对创建、支付、取消和超时调度记录。';
COMMENT ON COLUMN mq_consumed_message.aggregate_id IS '消费职责记录的业务聚合键；缓存和统计保存订单ID字符串，事务超时调度保存CREATE订单号，只作逻辑关联和排查。';
COMMENT ON COLUMN mq_consumed_message.consumed_at IS '消费者首次成功领取该幂等键的时间；业务事务回滚时本记录也随之回滚。';

CREATE INDEX idx_mq_consumed_time
    ON mq_consumed_message (consumed_at DESC, id DESC);

-- 缓存删除不需要单独数据库投影；订单统计则用单例行演示“幂等记录+业务UPSERT”同事务。
CREATE TABLE order_statistics (
    id SMALLINT PRIMARY KEY,
    created_count BIGINT NOT NULL DEFAULT 0,
    paid_count BIGINT NOT NULL DEFAULT 0,
    cancelled_count BIGINT NOT NULL DEFAULT 0,
    created_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    last_event_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_statistics_singleton CHECK (id = 1)
);

COMMENT ON TABLE order_statistics IS '订单统计投影表：由消息消费驱动，两套可靠消息方案的统计消费组都更新这一行，但各自先做组内幂等。';
COMMENT ON COLUMN order_statistics.id IS '单例主键；CHECK限制只能为1，使整表只维护一行全局学习统计。';
COMMENT ON COLUMN order_statistics.created_count IS '成功处理的ORDER_CREATED数量；重复投递不得再次累计。';
COMMENT ON COLUMN order_statistics.paid_count IS '成功处理的ORDER_PAID数量；支付不会触发商品缓存删除，但会进入统计。';
COMMENT ON COLUMN order_statistics.cancelled_count IS '成功处理的ORDER_CANCELLED数量；取消事件同时会让缓存组删除商品缓存。';
COMMENT ON COLUMN order_statistics.created_amount IS '创建订单事件的金额累计值；使用DECIMAL避免浮点金额误差。';
COMMENT ON COLUMN order_statistics.last_event_at IS '最近一次成功统计事件的消费时间；用于观察统计投影推进到的处理时间。';
COMMENT ON COLUMN order_statistics.updated_at IS '统计行最近一次UPSERT时间；表示投影更新时间而不是订单事实更新时间。';

-- RocketMQ事务消息的通用持久回查依据。表结构只表达协调语义，不绑定订单等具体业务领域。
CREATE TABLE mq_transaction_record (
    transaction_id VARCHAR(36) PRIMARY KEY,
    business_type VARCHAR(50) NOT NULL,
    business_key VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mq_transaction_status
        CHECK (status IN ('PREPARED', 'COMMITTED', 'ROLLED_BACK')),
    CONSTRAINT ck_mq_transaction_id_not_blank
        CHECK (BTRIM(transaction_id) <> ''),
    CONSTRAINT ck_mq_transaction_business_type_not_blank
        CHECK (BTRIM(business_type) <> ''),
    CONSTRAINT ck_mq_transaction_business_key_not_blank
        CHECK (BTRIM(business_key) <> ''),
    CONSTRAINT ck_mq_transaction_operation_type_not_blank
        CHECK (BTRIM(operation_type) <> '')
);

COMMENT ON TABLE mq_transaction_record IS 'RocketMQ通用事务消息记录表：持久化半消息对应的本地事务状态，供Broker回查和孤儿PREPARED清理，不绑定特定业务领域。';
COMMENT ON COLUMN mq_transaction_record.transaction_id IS '应用生成的稳定UUID：既是事务记录主键，也是消息信封messageId；Broker回查直接使用该值，不另存重复消息ID。';
COMMENT ON COLUMN mq_transaction_record.business_type IS '业务类型：由调用方协议定义，例如ORDER或INVENTORY；基础设施只校验非空白，不限制具体枚举。';
COMMENT ON COLUMN mq_transaction_record.business_key IS '稳定业务键：例如订单号、退款单号或库存预占号；与业务类型和操作类型共同标识一次受控业务操作。';
COMMENT ON COLUMN mq_transaction_record.operation_type IS '本次本地业务操作类型：由调用方协议定义；基础设施只校验非空白，不限制CREATE等具体枚举。';
COMMENT ON COLUMN mq_transaction_record.status IS '事务状态：PREPARED等待裁决、COMMITTED本地业务事实已提交、ROLLED_BACK已明确回滚。';
COMMENT ON COLUMN mq_transaction_record.last_error IS '明确回滚的错误摘要；不确定提交结果不能据此猜测终态，应继续依据status回查。';
COMMENT ON COLUMN mq_transaction_record.created_at IS 'PREPARED记录创建时间；用于识别半消息发送前崩溃留下的过期孤儿记录。';
COMMENT ON COLUMN mq_transaction_record.updated_at IS '事务状态最后更新时间；COMMITTED或ROLLED_BACK条件更新时由数据库刷新。';

-- 只有PREPARED/COMMITTED占用业务键；ROLLED_BACK保留审计，但允许使用新transactionId受控重试。
CREATE UNIQUE INDEX uk_mq_transaction_active_business_operation
    ON mq_transaction_record (business_type, business_key, operation_type)
    WHERE status IN ('PREPARED', 'COMMITTED');
CREATE INDEX idx_mq_transaction_business_lookup
    ON mq_transaction_record (business_type, business_key, status, created_at DESC);
CREATE INDEX idx_mq_transaction_status_created
    ON mq_transaction_record (status, created_at, transaction_id);

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
