# XXL-JOB 初始化数据
#
# 本文件只插入数据；执行前提：先执行 ../schema/schema.sql。
# 可重复执行：执行器组和任务都使用固定主键，并通过 ON DUPLICATE KEY UPDATE 收敛到本文件定义的状态。
# 安全边界：所有任务在 INSERT 和 UPDATE 分支都强制 trigger_status=0，首次启动或手工重放脚本都不会自动调度。
# 学习建议：先启动并观察执行器完成注册，再到 Admin 页面逐个检查参数，最后手动启用需要学习的任务。

USE `xxl_job`;
SET NAMES utf8mb4;

START TRANSACTION;

-- 官方示例数据。
INSERT INTO `xxl_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
    VALUES (1, 'xxl-job-executor-sample', '通用执行器Sample', 0, NULL, now()),
           (2, 'xxl-job-executor-sample-ai', 'AI执行器Sample', 0, NULL, now());

INSERT INTO `xxl_job_info`(`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
                           `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
                           `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
                           `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`,
                           `child_jobid`)
VALUES (1, 1, '示例任务01', now(), now(), 'XXL', '', 'CRON', '0 0 0 * * ? *',
        'DO_NOTHING', 'FIRST', 'demoJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'GLUE代码初始化',
        now(), ''),
       (2, 2, 'Ollama示例任务', now(), now(), 'XXL', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'ollamaJobHandler', '{
    "input": "Java实现二叉树层序遍历",
    "prompt": "你是一个研发工程师，擅长解决技术类问题。",
    "model": "qwen3.5:0.8b"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'GLUE代码初始化',
        now(), ''),
       (3, 2, 'Dify示例任务', now(), now(), 'XXL', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'difyWorkflowJobHandler', '{
    "inputs":{
        "input":"查询班级各学科前三名"
    },
    "user": "xxl-job",
    "baseUrl": "http://localhost/v1",
    "apiKey": "app-OUVgNUOQRIMokfmuJvBJoUTN"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'GLUE代码初始化',
        now(), ''),
       (4, 2, 'OpenClaw示例任务', now(), now(), 'XXL', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'openClawJobHandler', '{
    "input": "查看下上海今天得天气，给出出游建议",
    "prompt": "你是一个出游助手，擅长做旅游规划"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'GLUE代码初始化',
        now(), '');

INSERT INTO `xxl_job_user`(`id`, `username`, `password`, `role`, `permission`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 1, NULL);

INSERT INTO `xxl_job_lock` (`lock_name`)
VALUES ('schedule_lock');

# address_type=0 表示自动注册。Spring 应用必须使用完全相同的 AppName，Admin 才会把在线地址归到本组。
INSERT INTO `xxl_job_group`
    (`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES
    (100, 'spring-feature-collection-executor', 'Spring Feature Collection 学习执行器', 0, NULL, NOW())
AS new
ON DUPLICATE KEY UPDATE
    `app_name` = new.`app_name`,
    `title` = new.`title`,
    `address_type` = 0,
    `address_list` = NULL,
    `update_time` = new.`update_time`;

# 字段理解：
# 1. schedule_type=NONE 的任务只会被手动、API 或父任务触发；CRON/FIX_RATE 启用后才按表达式运行。
# 2. misfire_strategy 只处理“错过调度时间”：DO_NOTHING 忽略，FIRE_ONCE_NOW 补偿触发一次。
# 3. executor_route_strategy 决定从执行器集群中选择哪些实例；1010 用 SHARDING_BROADCAST 广播到每个实例。
# 4. executor_block_strategy=SERIAL_EXECUTION 表示同一执行器上前一轮未结束时，后续触发进入串行队列。
# 5. executor_timeout 和 executor_fail_retry_count 都是调度中心策略，不等于业务 Handler 内部的幂等、租约或重试状态。
#
# 所有 executor_param 均为对应 Handler DTO 可直接解析的 JSON；不要改成带单引号或 Java 对象文本的格式。
INSERT INTO `xxl_job_info`
    (`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
     `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
     `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
     `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`,
     `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
VALUES
    # 1001：最小手动任务。先观察参数反序列化、Handler 日志以及成功回调的完整链路。
    (1001, 100, '01-基础手动触发：成功结果', NOW(), NOW(), 'xiongtao', '',
     'NONE', '', 'DO_NOTHING', 'FIRST',
     'xxlBasicJobHandler', '{"message":"hello XXL-JOB","outcome":"SUCCESS"}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：基础手动触发', NOW(),
     '', 0, 0, 0),

    # 1002：启用后每 30 秒触发。DO_NOTHING 表示 Admin 停机期间错过的时刻不会补跑。
    (1002, 100, '02-CRON：每30秒与忽略过期调度', NOW(), NOW(), 'xiongtao', '',
     'CRON', '0/30 * * * * ? *', 'DO_NOTHING', 'FIRST',
     'xxlBasicJobHandler', '{"message":"cron tick","outcome":"SUCCESS"}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：CRON 与 DO_NOTHING', NOW(),
     '', 0, 0, 0),

    # 1003：固定频率为 15 秒。FIRE_ONCE_NOW 用于观察错过调度后只补偿一次，而不是补齐全部次数。
    (1003, 100, '03-FIX_RATE：15秒与一次补偿', NOW(), NOW(), 'xiongtao', '',
     'FIX_RATE', '15', 'FIRE_ONCE_NOW', 'FIRST',
     'xxlBasicJobHandler', '{"message":"fixed rate tick","outcome":"SUCCESS"}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：FIX_RATE 与 FIRE_ONCE_NOW', NOW(),
     '', 0, 0, 0),

    # 1004：参数为空对象，由 Handler 展示 init、execute、destroy 等生命周期以及执行线程信息。
    (1004, 100, '04-Handler生命周期', NOW(), NOW(), 'xiongtao', '',
     'NONE', '', 'DO_NOTHING', 'FIRST',
     'xxlLifecycleJobHandler', '{}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：Handler 生命周期', NOW(),
     '', 0, 0, 0),

    # 1005：业务睡眠 10 秒，但调度超时为 5 秒，专门观察超时中断和最终执行状态。
    # 当前先固定 SERIAL_EXECUTION；学习阻塞策略时可在 Admin 页面切换为 DISCARD_LATER 或 COVER_EARLY 对比。
    (1005, 100, '05-超时与阻塞策略', NOW(), NOW(), 'xiongtao', '',
     'NONE', '', 'DO_NOTHING', 'FIRST',
     'xxlSlowJobHandler', '{"seconds":10}',
     'SERIAL_EXECUTION', 5, 0, 'BEAN', '', '学习案例：5秒超时与串行阻塞', NOW(),
     '', 0, 0, 0),

    # 1006：调度中心配置 2 次失败重试；DTO 的 failTimes=2 让同一 businessKey 前两次失败，第三次成功。
    # leaseSeconds 是业务侧防止并发重复处理的租约，不替代 XXL-JOB 的失败重试次数。
    (1006, 100, '06-失败重试与业务幂等', NOW(), NOW(), 'xiongtao', '',
     'NONE', '', 'DO_NOTHING', 'FIRST',
     'xxlRetryJobHandler', '{"businessKey":"retry-demo-001","failTimes":2,"leaseSeconds":60}',
     'SERIAL_EXECUTION', 0, 2, 'BEAN', '', '学习案例：两次失败重试与幂等键', NOW(),
     '', 0, 0, 0),

    # 1007：每天 01:00 汇总。businessDate 留空时，Handler 以“调度触发日的前一天”为业务日期。
    # 这样参数不需要每天修改；runVersion 和租约仍由业务层控制重复执行边界。
    (1007, 100, '07-每日订单汇总：动态业务日期', NOW(), NOW(), 'xiongtao', '',
     'CRON', '0 0 1 * * ? *', 'DO_NOTHING', 'FIRST',
     'xxlDailyOrderSummaryJobHandler', '{"runVersion":1,"leaseSeconds":120}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：每日01:00汇总前一天订单', NOW(),
     '', 0, 0, 0),

    # 1008：固定业务日期与更高 runVersion，用于手动重跑历史日并观察版本化幂等，而不会参与定时调度。
    (1008, 100, '08-订单汇总：指定日期重跑', NOW(), NOW(), 'xiongtao', '',
     'NONE', '', 'DO_NOTHING', 'FIRST',
     'xxlDailyOrderSummaryJobHandler', '{"businessDate":"2026-08-12","runVersion":2,"leaseSeconds":120}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：指定日期与版本化重跑', NOW(),
     '', 0, 0, 0),

    # 1009：生成 100 条工作项，按 failEvery 制造可重试失败；成功后通过 child_jobid 唤醒通用处理任务 1010。
    (1009, 100, '09-父任务：生成分片工作批次', NOW(), NOW(), 'xiongtao', '',
     'NONE', '', 'DO_NOTHING', 'FIRST',
     'xxlGenerateWorkBatchJobHandler', '{"batchKey":"learning-batch-001","itemCount":100,"failEvery":10,"failTimes":2}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：生成批次并触发子任务', NOW(),
     '1010', 0, 0, 0),

    # 1010：每5秒扫描所有未终结批次，每轮仍只领取 batchSize 条，既避免一个 JobThread 无界工作，
    # 又保证父任务第一次唤醒后剩余 PENDING/RETRY_WAIT 工作项仍有后续驱动。任务依然预置为停止状态，需学习者手工启用。
    # SHARDING_BROADCAST 会让执行器组内每个在线实例各收到一个分片序号与总分片数。
    (1010, 100, '10-子任务：分片处理工作项', NOW(), NOW(), 'xiongtao', '',
     'FIX_RATE', '5', 'DO_NOTHING', 'SHARDING_BROADCAST',
     'xxlProcessWorkItemsJobHandler', '{"batchSize":20,"maxAttempts":5,"leaseSeconds":60,"retryDelaySeconds":5}',
     'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '学习案例：分片广播处理批次', NOW(),
     '', 0, 0, 0)
AS new
ON DUPLICATE KEY UPDATE
    `job_group` = new.`job_group`,
    `job_desc` = new.`job_desc`,
    `update_time` = new.`update_time`,
    `author` = new.`author`,
    `alarm_email` = new.`alarm_email`,
    `schedule_type` = new.`schedule_type`,
    `schedule_conf` = new.`schedule_conf`,
    `misfire_strategy` = new.`misfire_strategy`,
    `executor_route_strategy` = new.`executor_route_strategy`,
    `executor_handler` = new.`executor_handler`,
    `executor_param` = new.`executor_param`,
    `executor_block_strategy` = new.`executor_block_strategy`,
    `executor_timeout` = new.`executor_timeout`,
    `executor_fail_retry_count` = new.`executor_fail_retry_count`,
    `glue_type` = new.`glue_type`,
    `glue_source` = new.`glue_source`,
    `glue_remark` = new.`glue_remark`,
    `glue_updatetime` = new.`glue_updatetime`,
    `child_jobid` = new.`child_jobid`,
    # 即使脚本在已有数据库上被手工重放，也必须把学习任务保持为停止状态，避免意外执行。
    `trigger_status` = 0,
    `trigger_last_time` = 0,
    `trigger_next_time` = 0;

COMMIT;
