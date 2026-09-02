-- V6：移除自定义 XXL-JOB 业务表名中的 learning 前缀。
-- 兼容已有数据库；新数据库由 Docker 初始化脚本直接创建新表名，因此本迁移为空操作。

DO $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN
        SELECT *
        FROM (VALUES
            ('xxl_learning_execution', 'xxl_execution'),
            ('xxl_learning_order_summary', 'xxl_order_summary'),
            ('xxl_learning_batch', 'xxl_batch'),
            ('xxl_learning_work_item', 'xxl_work_item'),
            ('xxl_learning_work_result', 'xxl_work_result')
        ) AS names(old_name, new_name)
    LOOP
        IF to_regclass(format('public.%I', item.old_name)) IS NOT NULL
           AND to_regclass(format('public.%I', item.new_name)) IS NULL THEN
            EXECUTE format(
                'ALTER TABLE public.%I RENAME TO %I',
                item.old_name,
                item.new_name
            );
        END IF;
    END LOOP;

    FOR item IN
        SELECT c.relname AS old_name,
               replace(c.relname, 'idx_xxl_learning_', 'idx_xxl_') AS new_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'i'
          AND c.relname LIKE 'idx_xxl_learning_%'
    LOOP
        EXECUTE format(
            'ALTER INDEX public.%I RENAME TO %I',
            item.old_name,
            item.new_name
        );
    END LOOP;

    FOR item IN
        SELECT c.relname AS table_name,
               con.conname AS old_name,
               replace(con.conname, 'xxl_learning_', 'xxl_') AS new_name
        FROM pg_constraint con
        JOIN pg_class c ON c.oid = con.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND con.conname LIKE '%xxl_learning_%'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.%I RENAME CONSTRAINT %I TO %I',
            item.table_name,
            item.old_name,
            item.new_name
        );
    END LOOP;
END $$;
