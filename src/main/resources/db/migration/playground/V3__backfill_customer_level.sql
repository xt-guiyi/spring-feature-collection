-- V3：回填客户等级，然后把等级约束收紧为有默认值、非空且只能取合法值。

UPDATE flyway_migration.customer_account
SET customer_level = CASE
    WHEN total_spent >= 30000 THEN 'VIP'
    WHEN total_spent >= 10000 THEN 'GOLD'
    ELSE 'STANDARD'
END
WHERE customer_level IS NULL;

ALTER TABLE flyway_migration.customer_account
    ALTER COLUMN customer_level SET DEFAULT 'STANDARD';

ALTER TABLE flyway_migration.customer_account
    ALTER COLUMN customer_level SET NOT NULL;

ALTER TABLE flyway_migration.customer_account
    ADD CONSTRAINT ck_customer_account_customer_level
        CHECK (customer_level IN ('VIP', 'GOLD', 'STANDARD'));
