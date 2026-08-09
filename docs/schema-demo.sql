-- 1. 删除旧表（带 CASCADE 可以自动处理外键依赖）
DROP TABLE IF EXISTS order_products CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS id_cards CASCADE;
DROP TABLE IF EXISTS product_profiles CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 2. 创建新表（逻辑外键，不带 FOREIGN KEY 约束）

-- 用户表
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 身份证表（与用户一对一）
CREATE TABLE id_cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    card_number VARCHAR(18) NOT NULL,
    real_name VARCHAR(50)
);

-- 订单表（与用户一对多）
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    total_amount DECIMAL(10, 2),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商品表
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT
);

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
    CONSTRAINT uk_order_product UNIQUE (order_id, product_id)
);

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
