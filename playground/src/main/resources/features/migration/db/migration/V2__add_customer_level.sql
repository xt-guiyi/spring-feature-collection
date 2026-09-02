-- V2：以兼容式方式增加客户等级字段。
-- 先允许 NULL，给旧行和分批发布留出回填窗口；约束在 V3 再收紧。

ALTER TABLE flyway_migration.customer_account
    ADD COLUMN customer_level VARCHAR(20) NULL;

CREATE INDEX idx_customer_account_customer_level
    ON flyway_migration.customer_account (customer_level);
