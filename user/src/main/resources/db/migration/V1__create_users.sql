-- user-service 自己拥有 user_db.users 表；不要把此表作为 playground 的跨服务查询表。
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

-- 开发环境保留原学习数据中的固定用户 ID，Flowable 默认审批人依赖 1、2、3。
INSERT INTO users (id, username, email, status) VALUES
    (1, 'zhangsan', 'zhangsan@example.com', 'ACTIVE'),
    (2, 'lisi', 'lisi@example.com', 'ACTIVE'),
    (3, 'wangwu', 'wangwu@example.com', 'ACTIVE'),
    (4, 'zhaoliu', 'zhaoliu@example.com', 'ACTIVE'),
    (5, 'sunqi', 'sunqi@example.com', 'ACTIVE'),
    (6, 'zhouba', 'zhouba@example.com', 'ACTIVE'),
    (7, 'wujiu', 'wujiu@example.com', 'ACTIVE'),
    (8, 'zhengshi', 'zhengshi@example.com', 'ACTIVE'),
    (9, 'heyi', 'heyi@example.com', 'ACTIVE'),
    (10, 'linshi', 'linshi@example.com', 'ACTIVE'),
    (11, 'chenba', 'chenba@example.com', 'ACTIVE'),
    (12, 'gaojiu', 'gaojiu@example.com', 'ACTIVE');

SELECT setval(
    pg_get_serial_sequence('users', 'id'),
    (SELECT MAX(id) FROM users),
    true
);
