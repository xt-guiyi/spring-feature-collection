-- PostgreSQL Playground 业务表和示例数据初始化。
-- 本文件只在 PostgreSQL 数据卷首次初始化时执行。

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
COMMENT ON COLUMN orders.id IS '订单主键：由数据库序列自动生成，供订单明细关联及延迟取消在本地定位订单。';
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

CREATE INDEX idx_product_profiles_attributes_gin
    ON product_profiles USING GIN (attributes);

-- 频繁按照某一个固定属性等值查询时，表达式 B-Tree 往往比“什么都交给 GIN”更直接。
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

-- RocketMQ 消息消费记录
CREATE TABLE mq_consumed_message (
    consumer_group VARCHAR(100) NOT NULL,
    consume_id VARCHAR(36) NOT NULL,
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mq_consumed_message PRIMARY KEY (consumer_group, consume_id)
);

COMMENT ON TABLE mq_consumed_message IS 'RocketMQ消息消费记录表。';
COMMENT ON COLUMN mq_consumed_message.consumer_group IS '消费组名称。';
COMMENT ON COLUMN mq_consumed_message.consume_id IS '消费ID。';
COMMENT ON COLUMN mq_consumed_message.consumed_at IS '消息消费时间。';

-- 订单消息消费统计
CREATE TABLE order_statistics (
    id SMALLINT PRIMARY KEY,
    created_count BIGINT NOT NULL DEFAULT 0,
    paid_count BIGINT NOT NULL DEFAULT 0,
    cancelled_count BIGINT NOT NULL DEFAULT 0,
    created_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    last_consumed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_statistics_singleton CHECK (id = 1)
);

COMMENT ON TABLE order_statistics IS '订单消息消费统计表。';
COMMENT ON COLUMN order_statistics.id IS '统计记录主键。';
COMMENT ON COLUMN order_statistics.created_count IS '已创建订单数量。';
COMMENT ON COLUMN order_statistics.paid_count IS '已支付订单数量。';
COMMENT ON COLUMN order_statistics.cancelled_count IS '已取消订单数量。';
COMMENT ON COLUMN order_statistics.created_amount IS '已创建订单总金额。';
COMMENT ON COLUMN order_statistics.last_consumed_at IS '最近消息消费时间。';
COMMENT ON COLUMN order_statistics.updated_at IS '统计更新时间。';

-- RocketMQ 事务消息记录
CREATE TABLE mq_transaction_record (
    transaction_id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mq_transaction_status
        CHECK (status IN ('PREPARED', 'COMMITTED', 'ROLLED_BACK'))
);

COMMENT ON TABLE mq_transaction_record IS 'RocketMQ事务消息记录表。';
COMMENT ON COLUMN mq_transaction_record.transaction_id IS '事务ID。';
COMMENT ON COLUMN mq_transaction_record.status IS '事务状态。';
COMMENT ON COLUMN mq_transaction_record.last_error IS '最近错误信息。';
COMMENT ON COLUMN mq_transaction_record.created_at IS '记录创建时间。';
COMMENT ON COLUMN mq_transaction_record.updated_at IS '记录更新时间。';

CREATE INDEX idx_mq_transaction_status_created
    ON mq_transaction_record (status, created_at, transaction_id);

-- 3. 插入示例数据

INSERT INTO users (username, email, phone, status) VALUES
('zhangsan', 'zhangsan@example.com', '13800138000', 'ACTIVE'),
('lisi', 'lisi@example.com', '13900139000', 'ACTIVE'),
('wangwu', 'wangwu@example.com', '13700139001', 'ACTIVE'),
('zhaoliu', 'zhaoliu@example.com', '13700139002', 'ACTIVE'),
('sunqi', 'sunqi@example.com', '13700139003', 'ACTIVE'),
('zhouba', 'zhouba@example.com', '13700139004', 'ACTIVE'),
('wujiu', 'wujiu@example.com', '13700139005', 'ACTIVE'),
('zhengshi', 'zhengshi@example.com', '13700139006', 'ACTIVE'),
('heyi', 'heyi@example.com', '13700139007', 'ACTIVE'),
('linshi', 'linshi@example.com', '13700139008', 'ACTIVE'),
('chenba', 'chenba@example.com', '13700139009', 'ACTIVE'),
('gaojiu', 'gaojiu@example.com', '13700139010', 'ACTIVE');

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
