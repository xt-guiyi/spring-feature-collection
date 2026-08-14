# XXL-JOB 定时任务调度学习手册

> 本文对应 XXL-JOB `3.4.2`、Spring Boot `4.1.0` 和 Java `21`。
> 代码使用当前版本的 `@XxlJob + XxlJobHelper` API，不使用旧教程中的 `ReturnT<String>`。

## 1. 这个模块解决什么问题

Spring 的 `@Scheduled` 很适合单应用里的简单定时调用，但它本身不提供完整的任务控制台、执行器注册、
路由策略、失败重试、分片广播、人工触发和集中日志。XXL-JOB 把“何时、在哪台机器触发”交给调度中心，
把“真正执行什么业务”留在业务应用中。

本案例刻意拆成两类数据：

| 数据 | 存储位置 | 作用 |
|---|---|---|
| Cron、路由、重试次数、执行器注册、调度日志 | XXL-JOB Admin 的 MySQL | 说明调度中心触发过什么、回调结果是什么 |
| 订单、日报、执行台账、工作项、幂等结果 | Playground PostgreSQL | 说明业务是否已经执行、是否可以安全重做 |

因此，MySQL 只属于 XXL-JOB Admin，现有业务并没有从 PostgreSQL 迁走。

```text
浏览器
  │
  ▼
XXL-JOB Admin :18083 ──────► MySQL 8.4
  │  触发、终止、读取日志
  ▼
Spring Executor :19999 ────► PostgreSQL demo
  │
  └── 注册、心跳、结果回调 ─► XXL-JOB Admin
```

注意箭头是双向的：

- Executor 必须能访问 Admin，才能注册和回调执行结果；
- Admin 必须能访问 Executor 的内嵌端口，才能触发、终止和读取执行日志；
- Spring Web 端口 `3379/4379` 与 Executor 内嵌端口 `19999` 是两个不同端口。

## 2. 核心概念先建立起来

### 2.1 Admin、Executor 与 JobHandler

- **Admin**：调度中心，保存任务配置、计算下一次触发时间、选择执行器并记录调度日志。
- **Executor**：嵌入当前 Spring 应用的执行端，向 Admin 注册，并暴露一个内嵌 Netty 服务。
- **AppName**：执行器集群的稳定名称。同一 AppName 的多个进程属于同一执行器集群。
- **JobHandler**：真正执行 Java 业务的方法。Admin 中填写的 Handler 名必须和 `@XxlJob` 的值完全一致。

```java
@XxlJob("xxlBasicJobHandler")
public void execute() {
    String parameter = XxlJobHelper.getJobParam();
    XxlJobHelper.log("任务参数：{}", parameter);
}
```

当前版本推荐无参数、`void` 方法：

- 正常返回时，默认结果为成功；
- 业务明确失败时，可以调用 `XxlJobHelper.handleFail(...)`；
- 出现异常时应继续向上抛出，让 Admin 记录失败；
- 如果 catch 异常后只打印日志，方法正常返回，Admin 会被误导为执行成功。

### 2.2 一次执行有哪些标识

`XxlJobHelper` 可以读取：

- `jobId`：Admin 中任务配置的 ID；
- `logId`：本次调度日志 ID，每次人工触发和重试都可能不同；
- `logDateTime`：本次调度时间；
- `logFileName`：本次 Executor Rolling Log 文件；
- `shardIndex/shardTotal`：分片广播时当前分片序号和总数；
- `jobParam`：Admin 配置或人工触发时传入的字符串参数。

`logId` 适合排障，但不适合做业务幂等键。重试会产生新的执行上下文，而“2026-08-12 的订单日报”仍然是同一件业务工作。

### 2.3 调度类型

XXL-JOB 3.4.2 实际可用的调度类型只有：

- `NONE`：不自动调度，只能人工触发或作为子任务触发；
- `CRON`：按照 Quartz Cron 表达式触发；
- `FIX_RATE`：按照固定秒数触发。

当前源码中的 `FIX_DELAY` 仍被注释，不要把旧文章或其他调度框架的固定延迟能力误认为本版本已经提供。

### 2.4 调度过期策略

当 Admin 停机一段时间后恢复，原计划时间可能已经过去：

- `DO_NOTHING`：错过的本次不补，等待下一次计划时间；
- `FIRE_ONCE_NOW`：恢复后立即补触发一次，而不是把错过的每一轮全部补齐。

是否补触发是调度策略；补触发后业务是否可以安全重做，仍由业务幂等保证。

### 2.5 路由策略

同一 AppName 有多个在线 Executor 时，Admin 可选择：

- 第一台、最后一台、轮询、随机；
- 一致性哈希、最不经常使用、最近最少使用；
- 故障转移、忙碌转移；
- 分片广播。

路由策略决定“这次请求发给谁”，不等于分布式事务或 exactly-once。

### 2.6 阻塞策略

同一个任务的上一次执行还没结束，新触发又到达同一 Executor 时：

- `SERIAL_EXECUTION`：单机串行，后来的进入队列；
- `DISCARD_LATER`：丢弃后来的触发；
- `COVER_EARLY`：终止先前线程，执行新的触发。

“单机”是关键词。这些策略只管理某个 Executor 上的 JobThread，不能代替跨进程业务幂等或数据库锁。

### 2.7 超时、终止与线程中断

XXL-JOB 的超时和人工终止最终依赖 `Thread.interrupt()`。因此：

- `InterruptedException` 不能被吞掉；
- 长循环应定期检查中断状态；
- 某些 JDBC、HTTP 或纯 CPU 代码不一定立即响应中断；
- Admin 显示超时，不代表业务副作用已经自动回滚。

本案例的 `xxlSlowJobHandler` 每秒休眠并把中断继续抛出，用来观察这一边界。

## 3. 第一次启动

### 3.1 初始化 PostgreSQL 学习表

XXL-JOB Admin 的 MySQL 表由容器首次创建空数据卷时自动初始化；当前业务应用使用的五张 PostgreSQL 表需要手工执行：

```bash
cd /Users/xiongtao/workspace/backend/spring-feature-collection

docker exec -i local-postgres psql -U root -d demo \
  < docs/xxl-job-learning-schema.sql
```

该脚本会删除并重建 5 张 `xxl_learning_*` 表，以便学习时始终得到当前最新结构；它不会删除
`orders` 或 `mq_*` 等其他模块数据。已经积累 XXL-JOB 学习记录时，执行前需要先确认可以清空。

### 3.2 启动 Admin 和它的 MySQL

```bash
cd /Users/xiongtao/workspace/backend/docker
docker compose up -d xxl-job-mysql xxl-job-admin
```

浏览器打开：

```text
http://localhost:18083
```

首次登录使用官方默认账号：

```text
用户名：admin
密码：123456
```

这只是本地学习默认值。真实环境必须修改登录密码，并通过环境变量覆盖数据库密码和 AccessToken。

MySQL 的两个初始化脚本只在 `xxl_job_mysql_data` 为空时执行：

1. `10-tables_xxl_job.sql` 创建官方表和默认管理员；
2. `20-learning-jobs.sql` 预置本案例执行器组及 10 条停止状态任务。

重启容器不会重复初始化。不要为了“重新加载任务”随意执行 `docker compose down -v`，因为 `-v` 会永久删除任务配置、用户和调度日志。

### 3.3 启动第一个 Executor

IDE 启动应用前增加环境变量：

```text
XXL_JOB_EXECUTOR_ENABLED=true
XXL_JOB_ACCESS_TOKEN=xxl-job-learning-token
```

默认通信路径是：

```text
Executor -> http://127.0.0.1:18083
Admin   -> http://host.docker.internal:19999
```

进入 Admin 的“执行器管理”，应看到 `spring-feature-collection-executor` 的注册地址。若执行器在线但任务触发失败，重点检查的不是 Spring Web 端口，而是 `19999` 是否能从 Admin 容器访问。

### 3.4 启动第二个 Executor

要观察轮询、故障转移和分片广播，可再启动一个 IDE 实例。两个实例使用相同 AppName，但端口不同。

第一个实例：

```text
SERVER_PORT=4379
XXL_JOB_EXECUTOR_ENABLED=true
XXL_JOB_EXECUTOR_PORT=19999
XXL_JOB_EXECUTOR_ADDRESS=http://host.docker.internal:19999
```

第二个实例：

```text
SERVER_PORT=4380
XXL_JOB_EXECUTOR_ENABLED=true
XXL_JOB_EXECUTOR_PORT=20000
XXL_JOB_EXECUTOR_ADDRESS=http://host.docker.internal:20000
```

不能只改 Spring `SERVER_PORT`。Executor 的 Netty 端口也必须唯一，否则第二个进程会端口冲突。

## 4. 预置任务说明

所有预置任务都处于停止状态，不会在第一次启动 Admin 时自动修改业务数据。

| ID | 任务 | Handler | 主要观察点 |
|---:|---|---|---|
| 1001 | 基础手动任务 | `xxlBasicJobHandler` | 参数、默认成功、显式失败、异常 |
| 1002 | 基础 Cron | `xxlBasicJobHandler` | CRON、DO_NOTHING |
| 1003 | 固定频率 | `xxlBasicJobHandler` | FIX_RATE、FIRE_ONCE_NOW |
| 1004 | 生命周期任务 | `xxlLifecycleJobHandler` | init/destroy 的真实触发时机 |
| 1005 | 慢任务 | `xxlSlowJobHandler` | 阻塞策略、超时、终止、中断 |
| 1006 | 失败重试 | `xxlRetryJobHandler` | 重试会重复进入业务代码 |
| 1007 | 每日订单汇总 | `xxlDailyOrderSummaryJobHandler` | 调度日期、版本化幂等 |
| 1008 | 手工重算日报 | `xxlDailyOrderSummaryJobHandler` | 指定日期、高版本覆盖 |
| 1009 | 生成工作批次 | `xxlGenerateWorkBatchJobHandler` | 父任务、幂等造数 |
| 1010 | 分片处理工作项 | `xxlProcessWorkItemsJobHandler` | 5 秒周期续跑、分片、SKIP LOCKED、租约、结果唯一键 |

### 4.1 基础任务

默认成功：

```json
{
  "message": "hello XXL-JOB",
  "outcome": "SUCCESS"
}
```

将 `outcome` 改成 `FAIL` 可观察显式 `handleFail`；改成 `EXCEPTION` 可观察抛异常。两者都会显示失败，但调用路径不同：

- 已知的业务拒绝可以显式标记失败；
- 未预期异常应记录完整堆栈并继续抛出。

### 4.2 生命周期任务

任务参数可以使用空 JSON：

```json
{}
```

`init` 在该任务的 JobThread 创建时执行，`destroy` 在 JobThread 被替换、删除、Executor 停止，或线程连续空闲后被回收时执行，
二者都不是“每次调度之前和之后”。因此学习时即使没有删除任务，也可能在空闲一段时间后看到 `destroy` 日志。

### 4.3 慢任务

```json
{
  "seconds": 10
}
```

建议按顺序练习：

1. 执行超时设置为 5 秒，观察中断；
2. 改为单机串行，快速人工触发两次；
3. 改为丢弃后续，再触发两次；
4. 改为覆盖之前，再触发两次；
5. 执行期间点击终止，观察 Executor 日志和 Admin 执行结果。

不要把覆盖之前理解为事务回滚。若前一个线程已经提交数据库，它产生的结果不会因为线程被中断而自动消失。

### 4.4 失败重试与稳定业务键

```json
{
  "businessKey": "retry-demo-001",
  "failTimes": 2,
  "leaseSeconds": 60
}
```

任务前两次进入业务方法时故意失败，第三次成功。可以同时观察：

- Admin 生成多次执行记录；
- PostgreSQL 中只有一个稳定 `execution_key`；
- `attempt_count` 增加；
- 已成功后再次人工触发，会作为幂等重复跳过，而不是再次产生副作用。

这说明“配置失败重试”与“业务支持安全重试”必须同时存在。

### 4.5 每日订单汇总

自动日报参数：

```json
{
  "runVersion": 1,
  "leaseSeconds": 120
}
```

未传 `businessDate` 时，Handler 不使用 `LocalDate.now()`，而是根据本次 XXL-JOB 触发的 `logDateTime`
转换为上海日期后减一天。这比读取任务真正开始执行时的系统日期更准确，但要注意：XXL-JOB 失败重试会创建新的触发，
新的 `logDateTime` 也会变化。若任务可能跨午夜重试，应像手工重算一样显式传入 `businessDate`，不要把框架时间当作业务幂等键。

当前 `orders.created_at` 是不带时区的 PostgreSQL `TIMESTAMP`，本案例把它约定为上海业务墙钟时间，日报使用
`[当天 00:00, 次日 00:00)` 查询。若生产系统统一按 UTC 保存，建议把事实时间改为 `TIMESTAMPTZ`，并在查询时
显式按业务时区转换；不能只把 Java 日期换成上海时区就假设数据库中的无时区时间也自动改变含义。

手工重算参数：

```json
{
  "businessDate": "2026-08-12",
  "runVersion": 2,
  "leaseSeconds": 120
}
```

同日期、同版本重复触发会跳过；只有更高版本可以有意覆盖旧汇总，旧版本不得反向覆盖新版本。

### 4.6 父任务生成批次

```json
{
  "batchKey": "learning-batch-001",
  "itemCount": 100,
  "failEvery": 10,
  "failTimes": 2
}
```

- `batchKey` 是稳定业务键；
- 同参数重复生成不会重复插入工作项；
- 相同 `batchKey` 配不同参数会失败，避免悄悄混合两次实验；
- 父任务成功后，Admin 触发 ID `1010` 子任务。

父任务与子任务不是一个数据库事务。父任务成功只表示子任务得到了第一次调度机会，子任务仍必须重新读取 PostgreSQL 工作项。
由于 1010 每轮最多领取 `batchSize` 条，而且失败项要等到 `retryDelaySeconds` 后才能再次领取，单次子任务触发不可能保证
100 条工作全部收口。学习完整批处理时，应先在 Admin 启用 1010 的 5 秒固定频率，再触发 1009；它会保持每轮有界，
同时持续驱动 `PENDING/RETRY_WAIT` 项直到批次终态。所有任务初始仍是停止状态，不会自动改数据。
观察到批次进入 `SUCCESS`、`PARTIAL_SUCCESS` 或 `FAILED` 后，应在 Admin 停止 1010；代码会跳过终态批次，
但一直启用固定频率仍会让 Admin 持续产生没有业务工作的调度日志。

同一个 `batchKey` 的处理阶段也应固定 `maxAttempts`、`leaseSeconds` 和 `retryDelaySeconds`。第一版把这组参数放在
Admin 任务配置中而不是批次表里，因此批次处理中途不要修改它们；生产系统通常会把处理策略或策略版本随批次持久化，
并拒绝同一业务批次发生策略漂移。

`maxAttempts` 是每个工作项的总尝试上限，不是“计划失败后的额外次数”。因此若生成参数 `failTimes=20`，处理参数却是
`maxAttempts=5`，该工作项会在第 5 次按设计进入 `DEAD`，不会等到第 21 次成功。要演示最终成功，应满足
`maxAttempts > failTimes`。

### 4.7 分片处理工作项

```json
{
  "batchKey": "learning-batch-001",
  "batchSize": 20,
  "maxAttempts": 5,
  "leaseSeconds": 60,
  "retryDelaySeconds": 5
}
```

任务使用固定 64 个逻辑桶：

```text
bucket_no = 工作项稳定哈希落桶结果
当前分片负责：bucket_no % shardTotal == shardIndex
```

数据库领取使用：

```sql
SELECT ...
FOR UPDATE SKIP LOCKED
```

然后在短事务内将候选工作项改为 `RUNNING` 并生成租约令牌。这样多个 Executor 可以并行领取，又不会互相等待同一批已锁定行。

这里把“每轮只取 20 条”和“每 5 秒再调度一轮”组合起来：前者限制单次占用时间，后者负责持续驱动。
如果只配置父任务的一次 `child_jobid` 触发、却没有周期扫描或其他可靠续触发，剩余工作项会永久停留，不能称为完整批处理。

最终安全仍来自多层约束：

1. 工作项 `RUNNING` 状态和租约限制谁能回写；
2. 处理结果表对工作项及业务键设置唯一约束；
3. 失败项进入 `RETRY_WAIT`，达到最大次数后进入 `DEAD`；
4. Executor 崩溃后，只有租约过期的工作项 `RUNNING` 才允许被其他实例接管。

分片数量变化可能让桶重新分配给另一实例，但租约和幂等唯一键保证业务结果不会因此重复。

## 5. 业务观察接口

Admin 负责任务增删改、启停和人工触发；当前项目不重复实现一套管理后台，只提供业务状态的只读查询。

### 5.1 执行台账

```http
GET /api/playground/xxl-job/operations/executions
    ?handlerName=xxlRetryJobHandler
    &status=SUCCESS
    &pageNum=1
    &pageSize=10

GET /api/playground/xxl-job/operations/executions/{id}
```

重点对照 `executionKey`、`attemptCount`、`jobId`、`logId`、租约和错误，而不是只看 Admin 是否显示绿色。

### 5.2 订单日报

```http
GET /api/playground/xxl-job/operations/order-summaries
    ?dateFrom=2026-08-01
    &dateTo=2026-08-31
    &pageNum=1
    &pageSize=10
```

### 5.3 批次、工作项和结果

```http
GET /api/playground/xxl-job/operations/batches?status=PROCESSING&pageNum=1&pageSize=10

GET /api/playground/xxl-job/operations/batches/learning-batch-001/items
    ?status=RETRY_WAIT
    &pageNum=1
    &pageSize=10

GET /api/playground/xxl-job/operations/results
    ?batchKey=learning-batch-001
    &pageNum=1
    &pageSize=10
```

所有接口继续使用项目统一的 `Result<T>` 和 `PageResult<T>`，不会直接暴露数据库实体。

## 6. 为什么需要业务执行台账

一次任务可能出现以下时间线：

```text
Admin 触发成功
       └─ Executor 业务提交成功
       └─ Executor 回调时网络断开
            └─ Admin 仍然认为状态未知或失败
```

如果此时只相信 Admin 日志并重新执行，业务可能重复。反过来，Admin 显示触发成功，也只说明请求送达 Executor，不说明业务事务一定提交。

所以需要区分四个结果：

1. **调度结果**：Admin 是否成功选择并调用 Executor；
2. **执行结果**：Handler 最终上报成功、失败或超时；
3. **回调结果**：Executor 的执行结果是否成功写回 Admin；
4. **业务结果**：PostgreSQL 中稳定业务事实是否已提交。

XXL-JOB 提供的是至少可能重复的任务触发环境，不提供业务 exactly-once。业务要依靠：

- 稳定业务幂等键和唯一约束；
- 带状态条件的原子更新；
- 短事务领取与租约过期接管；
- 外部 API 的幂等键；
- 发送消息时的 Outbox。

## 7. 租约为什么必须带令牌

只有 `locked_until` 还不够。考虑：

```text
Worker A 领取任务，处理很慢
  └─ 租约过期
       └─ Worker B 接管并成功
       └─ Worker A 恢复运行，晚到地写回结果
```

如果 A 只按主键更新，就会覆盖 B 的新结果。正确回写条件必须同时匹配：

```sql
WHERE id = :id
  AND status = 'RUNNING'
  AND lease_token = :tokenHeldByCurrentWorker
```

受影响行数为 0 表示当前 Worker 已失去所有权，不能把旧结果覆盖回去。

## 8. 常见问题排查

### 8.1 Executor 一直不在线

按顺序检查：

1. `XXL_JOB_EXECUTOR_ENABLED` 是否为 `true`；
2. Executor 日志中是否成功访问 `http://127.0.0.1:18083`；
3. Admin 与 Executor 的 AccessToken 是否完全一致；
4. Admin 地址是否误加了旧版 `/xxl-job-admin` 后缀；
5. AppName 是否与预置执行器组一致。

### 8.2 在线但触发失败

从 Admin 容器的视角检查注册地址。宿主机 IDE 场景应使用：

```text
http://host.docker.internal:19999
```

不能填写 `localhost:19999`，因为容器中的 localhost 指向 Admin 容器自己。

### 8.3 找不到 JobHandler

- Admin 的 Handler 字符串必须与 `@XxlJob` 完全一致；
- Handler 所在对象必须是 Spring Bean；
- 同一个 Spring 容器内不能出现重复 Handler 名；
- 被配置在 `excluded-package` 下或 `@Lazy` 的 Bean 不会按普通方式注册。

### 8.4 Admin 有失败日志，但 PostgreSQL 已成功

这可能是业务提交后回调丢失。先查询业务执行台账，不要立即通过人工手段重复修改业务数据。

### 8.5 删除卷后任务不见了

`xxl_job_mysql_data` 保存任务配置、用户、注册信息和调度日志。`docker compose down -v` 删除卷后只能重新初始化，未备份的历史数据无法自动恢复。

## 9. 推荐学习顺序

1. 阅读 `XxlJobExecutorConfig`，理解为什么没有官方 Starter、为什么只创建一个 Executor；
2. 阅读参数解析器，理解 Admin 参数只是字符串，JSON 类型和校验由业务负责；
3. 运行基础、生命周期和慢任务，理解执行上下文、结果和线程中断；
4. 阅读重试 Service，理解稳定业务键、租约和条件回写；
5. 运行订单日报，理解业务日期和版本化重算；
6. 运行父子任务，理解“触发子任务”不是跨任务事务；
7. 启动两个 Executor，运行分片任务，观察固定桶、SKIP LOCKED、失败退避和接管；
8. 最后对照 Admin 日志与 PostgreSQL 运维接口，区分调度事实和业务事实。

第一版不启用 GLUE、命令行和任意 HTTP 任务。这些模式允许在控制台动态执行代码或访问外部系统，必须配套更严格的账号权限、审计、命令白名单和网络访问控制，不适合在第一次学习时默认开放。
