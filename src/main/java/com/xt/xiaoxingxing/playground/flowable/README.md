# Flowable 请假审批学习模块

本模块只在当前 Spring 后端提供一个请假审批案例，串起 DMN、BPMN、候选人领取、并行多实例、嵌入式子流程和 Operations 历史查询；不引入 Flowable REST Starter，所有 HTTP 接口都是项目自己的 Controller。

## 和 RocketMQ 模块的分层对应

| RocketMQ 学习模块 | Flowable 学习模块 | 责任 |
| --- | --- | --- |
| `config` | `config` | 客户端/引擎绑定、固定名称和运行参数 |
| `controller` | `controller` | 对外的业务接口，不直接拼引擎细节 |
| `dto`、`entity` | `dto/request`、`entity` | 输入校验和业务台账模型 |
| `repository`、`mapper` | `repository`、`mapper` | 业务表读写；`ACT_*` 由 Flowable 自己管理 |
| `service` | `service` | `FlowableLeaveService` 负责申请/详情/历史，`FlowableTaskService` 负责任务查询/领取/完成，定义服务负责流程与 DMN 定义 |
| `listener/checker` | `delegate` | 引擎回调中的 DMN 路由、审批聚合、结果落库 |
| `util` | `support` | 用户校验、任务视图和 Flowable 异常转换 |
| `dto/response` | `vo` | 稳定的接口返回模型 |

入口关系保持一对一：`FlowableLeaveController -> FlowableLeaveService` 负责申请、详情和历史，
`FlowableTaskController -> FlowableTaskService` 负责任务查询、领取和完成。请假详情里展示当前任务，
只是详情聚合的一部分，不代表任务操作也放在请假服务中。

## 流程模型职责

- DMN `leaveApprovalRoute` 只根据 `leaveDays` 返回 `approvalRoute`、`requiredRoles`、`requiredApprovalCount`；调整审批阈值时优先修改 DMN 表。
- BPMN `leaveApprovalProcess` 的 `determineApprovalRoute` 服务任务只调用一次 DMN，再由服务端解析并校验审批人；创建接口不提前重复执行 DMN。路线网关随后选择单经理任务或并行多实例任务。
- `approvalUserIds` 是多实例 collection，经理、HR、负责人各生成一个候选任务，全部实例完成后才进入 `aggregateApproval`。
- 聚合 Delegate 读取 `flowable_leave_approval`，任一 `REJECT` 走驳回分支；全部通过进入嵌入式 `approvedSubProcess`，依次记录通过结果、准备通知状态，最后把申请更新为 `APPROVED`。

独立的 `/decisions/leave-route/evaluate` 只评估 DMN 规则并返回路线，不检查 `users` 表；用户存在性和 `ACTIVE` 状态校验只发生在 `/leaves` 启动 BPMN 时。

## 表的边界

`ACT_*` 保存 BPMN 运行时、任务和历史事实，`ACT_DMN_*` 保存 DMN 定义与决策执行历史；`flowable_leave_request` 和 `flowable_leave_approval` 是本项目面向业务查询的独立台账。业务表不建立用户外键，流程 Delegate 会通过 `FlowableUserSupport` 查询现有 `users` 表，校验当前路线需要的审批人是否存在且为 `ACTIVE`。

## DMN 结果示例

```http
POST /api/playground/flowable/decisions/leave-route/evaluate
Content-Type: application/json

{"leaveDays":2}
```

```json
{"leaveDays":4,"leaveType":"ANNUAL"}
```

```json
{"leaveDays":7,"leaveType":"PERSONAL"}
```

三种天数分别返回：

| 天数 | `approvalRoute` | 任务 |
| --- | --- | --- |
| 1–2 | `MANAGER` | 经理 1 个 |
| 3–5 | `MANAGER_HR` | 经理、HR 并行 2 个 |
| 6–30 | `MANAGER_HR_LEADER` | 经理、HR、负责人并行 3 个 |

## 完整调用顺序

1. （可选）先调用上面的 DMN evaluate，观察路线和审批人数。
2. `POST /api/playground/flowable/leaves` 创建申请并启动流程；`requestNo` 是幂等号。
3. `GET /api/playground/flowable/tasks?candidateUser=2` 查询用户可领取的任务。
4. 对每个任务调用 `POST /api/playground/flowable/tasks/{taskId}/claim`，请求体为 `{"userId":2}`。
5. 领取成功后由同一用户调用 `POST /api/playground/flowable/tasks/{taskId}/complete`，请求体示例：`{"userId":2,"decision":"APPROVE","comment":"同意"}`；驳回时 `comment` 必填。
6. `GET /api/playground/flowable/leaves/{id}` 查看业务状态和活动任务。
7. `GET /api/playground/flowable/operations/leaves/{id}/history` 一次查看 DMN 执行、Flowable 历史任务和业务审批记录。

请假申请示例（按 2、4、7 天分别替换 `leaveDays`，`requestNo` 保证唯一）。每个 HTTP 请求只提交一个 JSON；审批人不由调用方传入，而是由服务端学习用解析器固定为：经理用户 `2`、HR 用户 `1`、负责人用户 `3`。

服务端仍会逐个检查这些用户是否存在且状态为 `ACTIVE`；因此 1、2 天和 3、4、5 天申请通常可以直接演示，6 天以上申请需要先在 `users` 表准备 ID 为 `3` 的 ACTIVE 用户（或者修改 `FlowableApprovalUserResolver` 中的学习映射）。

```json
{
  "requestNo": "LEAVE-20260830-001",
  "applicantId": 1,
  "leaveDays": 2,
  "leaveType": "ANNUAL",
  "reason": "个人安排"
}
```

4 天和 7 天请求仍然使用相同的请求结构，只需要修改 `leaveDays`；服务端会自动加入对应的 HR 和负责人。审批人之间不能重复的约束也由服务端固定映射校验。

## 初始化限制

`docker/init/postgres/30-flowable-learning-schema.sql` 按 Flowable 8.0.0 官方 PostgreSQL 脚本（common、engine、history、dmn）再追加业务表，并由 Compose 只读挂载。PostgreSQL 官方镜像只会在 `postgres_data` 空卷首次初始化时执行 `/docker-entrypoint-initdb.d` 文件；已有本地或远程 `demo` 数据库不会自动补表，需要人工执行同一 SQL。应用配置固定 `flowable.database-schema-update=false`，应用启动不会替代迁移脚本。
