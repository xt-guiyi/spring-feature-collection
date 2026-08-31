ALTER TABLE flyway_migration.customer_account
    ADD COLUMN gender VARCHAR(10) NOT NULL DEFAULT '男';

COMMENT ON COLUMN flyway_migration.customer_account.gender IS '性别';
