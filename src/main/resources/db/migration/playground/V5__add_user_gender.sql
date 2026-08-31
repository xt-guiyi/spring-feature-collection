ALTER TABLE flyway_migration.customer_account
    DROP COLUMN gender;

ALTER TABLE public.users
    ADD COLUMN gender VARCHAR(10) NOT NULL DEFAULT '男';

COMMENT ON COLUMN public.users.gender IS '用户性别，默认男';
