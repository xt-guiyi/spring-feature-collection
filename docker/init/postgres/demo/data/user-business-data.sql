-- demo 数据库业务示例数据。
-- 执行前提：先执行同级目录 ../schema/user-business-schema.sql。

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
('linshi', 'linshi@example.com', '13700138008', 'ACTIVE'),
('chenba', 'chenba@example.com', '13700138009', 'ACTIVE'),
('gaojiu', 'gaojiu@example.com', '13700138010', 'ACTIVE');

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
