-- XXL-JOB Playground 的 PostgreSQL 业务表。
--
-- 本脚本只保存执行器自己的幂等、租约、日报和分片工作状态；XXL-JOB Admin 的 xxl_job_* 元数据
-- 必须继续使用官方版本对应的独立数据库脚本，不能混入本学习库。
-- 这是学习项目的“全量重建脚本”：执行时会先删除 5 张学习表及其数据，再直接创建当前最新结构。
-- 脚本不包含旧字段迁移、旧约束删除等兼容逻辑，让初学者只看到最终正确的数据库结构。
-- 本项目统一采用“逻辑外键”：表中保留 execution_id、batch_id、work_item_id 等关联字段，
-- 但不创建 FOREIGN KEY。关联对象是否存在由 Service 在同一业务流程中校验和维护。

-- 按被关联关系的反顺序删表，便于阅读；当前没有数据库外键，因此不需要 CASCADE。
DROP TABLE IF EXISTS xxl_learning_work_result;
DROP TABLE IF EXISTS xxl_learning_work_item;
DROP TABLE IF EXISTS xxl_learning_batch;
DROP TABLE IF EXISTS xxl_learning_order_summary;
DROP TABLE IF EXISTS xxl_learning_execution;

CREATE TABLE xxl_learning_execution (
    id BIGSERIAL PRIMARY KEY,
    -- execution_key 是业务幂等键。Admin 的 log_id 每次失败重试都会变化，不能拿它做业务唯一键。
    execution_key VARCHAR(300) NOT NULL,
    handler_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    -- RUNNING 记录携带一次性租约令牌；迟到 worker 只有令牌仍匹配时才能写终态。
    lease_token VARCHAR(100),
    lease_expires_at TIMESTAMP,
    job_id BIGINT NOT NULL,
    log_id BIGINT NOT NULL,
    log_date_time BIGINT NOT NULL,
    log_file_name VARCHAR(500),
    shard_index INT NOT NULL,
    shard_total INT NOT NULL,
    result_message VARCHAR(1000),
    last_error VARCHAR(1000),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_xxl_learning_execution_key UNIQUE (execution_key),
    CONSTRAINT ck_xxl_learning_execution_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_xxl_learning_execution_attempt CHECK (attempt_count >= 1),
    CONSTRAINT ck_xxl_learning_execution_shard CHECK (
        shard_total > 0 AND shard_index >= 0 AND shard_index < shard_total
    ),
    CONSTRAINT ck_xxl_learning_execution_lease CHECK (
        (status = 'RUNNING' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL AND completed_at IS NULL)
        OR
        (status IN ('SUCCESS', 'FAILED') AND lease_token IS NULL AND lease_expires_at IS NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_xxl_learning_execution_handler_status
    ON xxl_learning_execution (handler_name, status, updated_at DESC, id DESC);
CREATE INDEX idx_xxl_learning_execution_lease
    ON xxl_learning_execution (status, lease_expires_at)
    WHERE status = 'RUNNING';

CREATE TABLE xxl_learning_order_summary (
    -- 一天只保存一个当前版本；受控重算必须提高 run_version 才能覆盖。
    summary_date DATE PRIMARY KEY,
    run_version INT NOT NULL,
    order_count BIGINT NOT NULL,
    pending_order_count BIGINT NOT NULL,
    paid_order_count BIGINT NOT NULL,
    cancelled_order_count BIGINT NOT NULL,
    total_amount DECIMAL(18, 2) NOT NULL,
    source_start_at TIMESTAMP NOT NULL,
    source_end_at TIMESTAMP NOT NULL,
    execution_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_xxl_learning_summary_version CHECK (run_version >= 1),
    CONSTRAINT ck_xxl_learning_summary_counts CHECK (
        order_count >= 0 AND pending_order_count >= 0 AND paid_order_count >= 0
        AND cancelled_order_count >= 0
        AND pending_order_count + paid_order_count + cancelled_order_count <= order_count
    ),
    CONSTRAINT ck_xxl_learning_summary_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_xxl_learning_summary_window CHECK (source_start_at < source_end_at)
);

CREATE INDEX idx_xxl_learning_summary_version
    ON xxl_learning_order_summary (run_version, summary_date DESC);

CREATE TABLE xxl_learning_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_key VARCHAR(200) NOT NULL,
    item_count INT NOT NULL,
    fail_every INT NOT NULL,
    fail_times INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    generated_execution_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_xxl_learning_batch_key UNIQUE (batch_key),
    CONSTRAINT ck_xxl_learning_batch_item_count CHECK (item_count BETWEEN 1 AND 10000),
    CONSTRAINT ck_xxl_learning_batch_fail_every CHECK (fail_every >= 0),
    CONSTRAINT ck_xxl_learning_batch_fail_times CHECK (fail_times BETWEEN 0 AND 20),
    CONSTRAINT ck_xxl_learning_batch_status CHECK (
        status IN ('READY', 'PROCESSING', 'RETRY_WAIT', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')
    )
);

CREATE INDEX idx_xxl_learning_batch_status
    ON xxl_learning_batch (status, created_at DESC, id DESC);

CREATE TABLE xxl_learning_work_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    item_no INT NOT NULL,
    -- 固定 64 个逻辑桶与当前执行器数量解耦；扩缩容时只改变 bucket_no % shard_total 的归属。
    bucket_no SMALLINT NOT NULL,
    planned_failures INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token VARCHAR(100),
    lease_expires_at TIMESTAMP,
    -- 同一次 Admin 调度循环不得再次领取刚刚失败且 retryDelay=0 的项目，否则会饿死后续项目。
    last_log_id BIGINT,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_xxl_learning_work_item_no UNIQUE (batch_id, item_no),
    CONSTRAINT ck_xxl_learning_work_item_no CHECK (item_no >= 1),
    CONSTRAINT ck_xxl_learning_work_item_bucket CHECK (bucket_no BETWEEN 0 AND 63),
    CONSTRAINT ck_xxl_learning_work_item_failures CHECK (planned_failures BETWEEN 0 AND 20),
    CONSTRAINT ck_xxl_learning_work_item_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_xxl_learning_work_item_status CHECK (
        status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCESS', 'DEAD')
    ),
    CONSTRAINT ck_xxl_learning_work_item_lease CHECK (
        (status = 'RUNNING' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL AND completed_at IS NULL)
        OR
        (status <> 'RUNNING' AND lease_token IS NULL AND lease_expires_at IS NULL)
    )
);

CREATE INDEX idx_xxl_learning_work_item_claim
    ON xxl_learning_work_item (batch_id, status, available_at, item_no);
CREATE INDEX idx_xxl_learning_work_item_lease
    ON xxl_learning_work_item (status, lease_expires_at)
    WHERE status = 'RUNNING';
CREATE INDEX idx_xxl_learning_work_item_bucket
    ON xxl_learning_work_item (batch_id, bucket_no, status, item_no);

CREATE TABLE xxl_learning_work_result (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    item_no INT NOT NULL,
    execution_id BIGINT NOT NULL,
    result_value VARCHAR(500) NOT NULL,
    job_id BIGINT NOT NULL,
    log_id BIGINT NOT NULL,
    shard_index INT NOT NULL,
    shard_total INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 一项只能产生一个成功副作用；任务重试不能插入第二份结果。
    CONSTRAINT uk_xxl_learning_work_result_item UNIQUE (work_item_id),
    CONSTRAINT uk_xxl_learning_work_result_batch_item UNIQUE (batch_id, item_no),
    CONSTRAINT ck_xxl_learning_work_result_shard CHECK (
        shard_total > 0 AND shard_index >= 0 AND shard_index < shard_total
    )
);

CREATE INDEX idx_xxl_learning_work_result_batch
    ON xxl_learning_work_result (batch_id, created_at DESC, id DESC);

-- ============================================================================
-- 数据库级中文注释
-- COMMENT 会写入 PostgreSQL 元数据，可以在 DataGrip、DBeaver 等工具中直接查看。
-- 这些注释不参与业务计算，但能让后续学习者不用翻 Java 代码也能理解表和字段。
-- ============================================================================

COMMENT ON TABLE xxl_learning_execution IS
    'XXL-JOB 执行记录表：保存一次业务任务的幂等状态、执行上下文、租约和最终结果。';
COMMENT ON COLUMN xxl_learning_execution.id IS
    '主键 ID，由 PostgreSQL BIGSERIAL 自增生成，用于数据库内部唯一标识一条执行记录。';
COMMENT ON COLUMN xxl_learning_execution.execution_key IS
    '业务幂等键，同一次业务执行必须保持不变；唯一约束用来防止重复调度产生两份业务结果。';
COMMENT ON COLUMN xxl_learning_execution.handler_name IS
    'XXL-JOB 执行器方法名，与 @XxlJob 注解中的 handler 名称对应。';
COMMENT ON COLUMN xxl_learning_execution.status IS
    '执行状态：RUNNING 表示正在执行，SUCCESS 表示成功，FAILED 表示最终失败。';
COMMENT ON COLUMN xxl_learning_execution.attempt_count IS
    '当前业务执行已尝试的次数，首次执行从 1 开始，重试时递增。';
COMMENT ON COLUMN xxl_learning_execution.lease_token IS
    '当前 worker 持有的一次性租约令牌；只有令牌仍匹配的 worker 才能提交执行结果。';
COMMENT ON COLUMN xxl_learning_execution.lease_expires_at IS
    '租约过期时间；过期后可由其他 worker 接管，用于恢复执行器宕机造成的卡死任务。';
COMMENT ON COLUMN xxl_learning_execution.job_id IS
    'XXL-JOB Admin 中的任务 ID，用于追溯这条记录由哪个调度任务触发。';
COMMENT ON COLUMN xxl_learning_execution.log_id IS
    'XXL-JOB Admin 生成的调度日志 ID，一次调度尝试对应一个 log_id，不适合单独作为业务幂等键。';
COMMENT ON COLUMN xxl_learning_execution.log_date_time IS
    'XXL-JOB 传入的调度日志时间，以 Unix 毫秒时间戳保存。';
COMMENT ON COLUMN xxl_learning_execution.log_file_name IS
    'XXL-JOB 执行日志文件名，用于运维排查时定位该次执行的日志。';
COMMENT ON COLUMN xxl_learning_execution.shard_index IS
    '当前分片序号，从 0 开始，必须小于 shard_total。';
COMMENT ON COLUMN xxl_learning_execution.shard_total IS
    '本次调度的总分片数，非分片任务通常为 1。';
COMMENT ON COLUMN xxl_learning_execution.result_message IS
    '任务成功或失败后记录的简要结果说明，便于在管理页面快速判断执行情况。';
COMMENT ON COLUMN xxl_learning_execution.last_error IS
    '最近一次失败的错误摘要；完整堆栈应保留在日志系统，不建议全部塞入该字段。';
COMMENT ON COLUMN xxl_learning_execution.started_at IS
    '该业务执行首次开始的时间。';
COMMENT ON COLUMN xxl_learning_execution.completed_at IS
    '该业务执行进入 SUCCESS 或 FAILED 终态的完成时间；RUNNING 状态必须为空。';
COMMENT ON COLUMN xxl_learning_execution.created_at IS
    '执行记录在数据库中的创建时间。';
COMMENT ON COLUMN xxl_learning_execution.updated_at IS
    '执行记录最后一次修改时间，可用于排查长时间未更新的异常任务。';

COMMENT ON TABLE xxl_learning_order_summary IS
    'XXL-JOB 订单日报表：每个业务日保存一条订单汇总结果，用于演示定时汇总和受控重算。';
COMMENT ON COLUMN xxl_learning_order_summary.summary_date IS
    '汇总所属的业务日期，同时作为主键，保证每天只保留一个当前版本。';
COMMENT ON COLUMN xxl_learning_order_summary.run_version IS
    '日报重算版本，首次生成为 1；需要覆盖旧结果时必须传入更大版本。';
COMMENT ON COLUMN xxl_learning_order_summary.order_count IS
    '该日期窗口内的订单总数。';
COMMENT ON COLUMN xxl_learning_order_summary.pending_order_count IS
    '该日期窗口内处于 PENDING 状态的订单数。';
COMMENT ON COLUMN xxl_learning_order_summary.paid_order_count IS
    '该日期窗口内处于 PAID 状态的订单数。';
COMMENT ON COLUMN xxl_learning_order_summary.cancelled_order_count IS
    '该日期窗口内处于 CANCELLED 状态的订单数。';
COMMENT ON COLUMN xxl_learning_order_summary.total_amount IS
    '该日期窗口内订单金额合计，DECIMAL 避免浮点数带来的金额精度误差。';
COMMENT ON COLUMN xxl_learning_order_summary.source_start_at IS
    '本次统计读取源订单的起始时间，通常包含该边界。';
COMMENT ON COLUMN xxl_learning_order_summary.source_end_at IS
    '本次统计读取源订单的结束时间，通常不包含该边界，与起始时间组成左闭右开区间。';
COMMENT ON COLUMN xxl_learning_order_summary.execution_id IS
    '生成这条日报的 xxl_learning_execution.id；这是逻辑关联字段，不创建数据库外键。';
COMMENT ON COLUMN xxl_learning_order_summary.created_at IS
    '该日报记录首次创建的时间。';
COMMENT ON COLUMN xxl_learning_order_summary.updated_at IS
    '该日报最后一次生成或受控重算的时间。';

COMMENT ON TABLE xxl_learning_batch IS
    'XXL-JOB 分片学习批次表：定义一批待处理工作项的规模、故障演示参数和整体状态。';
COMMENT ON COLUMN xxl_learning_batch.id IS
    '主键 ID，由 PostgreSQL BIGSERIAL 自增生成。';
COMMENT ON COLUMN xxl_learning_batch.batch_key IS
    '批次业务唯一键，用于防止重复创建同一批工作数据。';
COMMENT ON COLUMN xxl_learning_batch.item_count IS
    '该批次计划生成的工作项总数，学习案例限制在 1 至 10000。';
COMMENT ON COLUMN xxl_learning_batch.fail_every IS
    '故障注入间隔；大于 0 时，每第 N 个工作项被设为计划失败，0 表示不按间隔注入故障。';
COMMENT ON COLUMN xxl_learning_batch.fail_times IS
    '被选中的工作项在最终成功前需要模拟失败的次数。';
COMMENT ON COLUMN xxl_learning_batch.status IS
    '批次整体状态：READY、PROCESSING、RETRY_WAIT、SUCCESS、PARTIAL_SUCCESS 或 FAILED。';
COMMENT ON COLUMN xxl_learning_batch.generated_execution_id IS
    '生成该批次的 xxl_learning_execution.id；这是逻辑关联字段，不创建数据库外键。';
COMMENT ON COLUMN xxl_learning_batch.created_at IS
    '批次创建时间。';
COMMENT ON COLUMN xxl_learning_batch.updated_at IS
    '批次状态或统计信息最后一次更新时间。';
COMMENT ON COLUMN xxl_learning_batch.completed_at IS
    '批次进入 SUCCESS、PARTIAL_SUCCESS 或 FAILED 终态的完成时间，未完成时为空。';

COMMENT ON TABLE xxl_learning_work_item IS
    'XXL-JOB 分片工作项表：保存批次中每个可独立领取、重试和恢复的最小处理单元。';
COMMENT ON COLUMN xxl_learning_work_item.id IS
    '主键 ID，由 PostgreSQL BIGSERIAL 自增生成。';
COMMENT ON COLUMN xxl_learning_work_item.batch_id IS
    '所属 xxl_learning_batch.id；这是逻辑关联字段，不创建数据库外键。';
COMMENT ON COLUMN xxl_learning_work_item.item_no IS
    '工作项在批次内的序号，从 1 开始，与 batch_id 组成业务唯一键。';
COMMENT ON COLUMN xxl_learning_work_item.bucket_no IS
    '稳定的逻辑桶号，范围为 0 至 63；通过 bucket_no % shard_total 决定当前分片归属。';
COMMENT ON COLUMN xxl_learning_work_item.planned_failures IS
    '故障演示参数，表示该工作项在成功前应模拟失败的次数。';
COMMENT ON COLUMN xxl_learning_work_item.status IS
    '工作项状态：PENDING、RUNNING、RETRY_WAIT、SUCCESS 或 DEAD。';
COMMENT ON COLUMN xxl_learning_work_item.attempt_count IS
    '该工作项已被实际执行的次数，每次合法领取并尝试处理后递增。';
COMMENT ON COLUMN xxl_learning_work_item.available_at IS
    '下一次允许被 worker 领取的时间，可通过将它推迟来实现重试间隔。';
COMMENT ON COLUMN xxl_learning_work_item.lease_token IS
    '当前处理 worker 持有的一次性租约令牌；非 RUNNING 状态必须为空。';
COMMENT ON COLUMN xxl_learning_work_item.lease_expires_at IS
    '当前工作项租约的过期时间；过期后其他 worker 可重新领取宕机遗留的任务。';
COMMENT ON COLUMN xxl_learning_work_item.last_log_id IS
    '最近一次处理该项目的 XXL-JOB 调度日志 ID，用于防止同一调度循环重复领取刚失败的项目。';
COMMENT ON COLUMN xxl_learning_work_item.last_error IS
    '工作项最近一次执行失败的错误摘要。';
COMMENT ON COLUMN xxl_learning_work_item.created_at IS
    '工作项创建时间。';
COMMENT ON COLUMN xxl_learning_work_item.updated_at IS
    '工作项状态、租约或重试信息最后一次更新时间。';
COMMENT ON COLUMN xxl_learning_work_item.completed_at IS
    '工作项进入 SUCCESS 或 DEAD 终态的完成时间，未完成时为空。';

COMMENT ON TABLE xxl_learning_work_result IS
    'XXL-JOB 工作结果表：只保存工作项成功后的一份业务副作用，用唯一约束演示消费幂等。';
COMMENT ON COLUMN xxl_learning_work_result.id IS
    '主键 ID，由 PostgreSQL BIGSERIAL 自增生成。';
COMMENT ON COLUMN xxl_learning_work_result.work_item_id IS
    '对应 xxl_learning_work_item.id；通过唯一约束保证一个工作项最多生成一份结果，不创建外键。';
COMMENT ON COLUMN xxl_learning_work_result.batch_id IS
    '所属 xxl_learning_batch.id，与 item_no 组成第二层幂等唯一键，不创建外键。';
COMMENT ON COLUMN xxl_learning_work_result.item_no IS
    '工作项在批次内的序号，与 batch_id 组合后唯一。';
COMMENT ON COLUMN xxl_learning_work_result.execution_id IS
    '真正写入这份成功结果的 xxl_learning_execution.id，用于追踪具体执行尝试，不创建外键。';
COMMENT ON COLUMN xxl_learning_work_result.result_value IS
    '工作项成功后产生的学习示例结果值。';
COMMENT ON COLUMN xxl_learning_work_result.job_id IS
    '写入结果时对应的 XXL-JOB Admin 任务 ID。';
COMMENT ON COLUMN xxl_learning_work_result.log_id IS
    '写入结果时对应的 XXL-JOB 调度日志 ID。';
COMMENT ON COLUMN xxl_learning_work_result.shard_index IS
    '写入这份结果的分片序号，从 0 开始。';
COMMENT ON COLUMN xxl_learning_work_result.shard_total IS
    '写入这份结果时的总分片数。';
COMMENT ON COLUMN xxl_learning_work_result.created_at IS
    '成功结果首次落库的时间；幂等重试不应更改或重复写入该记录。';
