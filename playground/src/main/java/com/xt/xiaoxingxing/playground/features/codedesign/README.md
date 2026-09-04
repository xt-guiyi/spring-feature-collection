# Java 代码设计学习模块

本模块不提供 HTTP 接口，也不依赖 Spring 或外部中间件。31 个示例都使用简单的订单场景，
在同一个类中对比“直接写法”和“改进写法”，可以直接运行 `main` 观察结果。

这里学习的是日常代码如何表达得更清楚，不讲 GoF 设计模式。改进写法也不是固定答案：代码很短、
规则稳定时可以保持简单；当约束增多、修改频繁或多人协作时，再使用对应设计。

## 第一阶段：控制流程 `controlflow`

| 顺序 | 类 | 直接写法的问题 | 改进方式 | 观察结果 |
|---|---|---|---|---|
| 1 | `StateMachineDemo` | 状态流转散落在 `if/else` | 使用状态、事件和迁移表 | 合法事件完成迁移，非法事件被拒绝 |
| 2 | `LegalStateModelDemo` | 多个 boolean 能组合出矛盾状态 | 使用一个明确的订单状态 | 对象只能处于一种合法状态 |
| 3 | `ExhaustiveSwitchDemo` | `default` 隐藏遗漏的新枚举值 | 穷举每一个状态 | 新增状态时由编译器提醒处理 |
| 4 | `GuardClauseDemo` | 校验形成多层嵌套 | 不满足条件时提前失败 | 主流程从上到下保持清晰 |
| 5 | `ReadableConditionDemo` | 复杂布尔表达式难以理解 | 用业务名称表达条件 | 调用处直接读出判断意图 |
| 6 | `DecisionTableDemo` | 长 `if/else` 混合条件与结果 | 将条件和结果集中为决策表 | 相同订单得到相同处理结果 |
| 7 | `CommonTailDemo` | 每个分支重复相同收尾代码 | 分支只决定差异，结尾统一执行 | 公共收尾内容只保留一处 |
| 8 | `StepwiseFlowDemo` | 一个方法同时做所有步骤 | 拆成校验、转换、计算和组装 | 每一步职责和顺序都可直接看到 |

## 第二阶段：数据与业务模型 `modeling`

| 顺序 | 类 | 直接写法的问题 | 改进方式 | 观察结果 |
|---|---|---|---|---|
| 9 | `ValueObjectDemo` | `String`、`BigDecimal` 无法表达业务含义 | 使用 `OrderId`、`Money` 值对象 | 非法值在创建时被阻止 |
| 10 | `ParameterObjectDemo` | 业务方法签名塞入大量零散参数 | 使用 `CreateOrderCommand` | 一次操作所需参数可以整体传递 |
| 11 | `DomainBehaviorDemo` | 外部代码可以随意改订单状态 | 由对象方法保护业务约束 | 非法业务操作被对象拒绝 |
| 12 | `AggregateConsistencyDemo` | 外部同时维护明细和总额 | 总额统一由订单明细派生 | 新增明细后总额仍然一致 |
| 13 | `IdentitySemanticsDemo` | 使用全部字段判断是否同一订单 | 使用订单 ID 表达身份 | 内容变化后仍能识别同一订单 |
| 14 | `ImmutableOrderDemo` | 可变对象让所有旧引用同时看到新状态 | 修改时创建新的订单快照 | 原订单内容保持不变 |
| 15 | `DefensiveCopyDemo` | 直接保存并暴露外部 List | 构造时复制并只暴露不可变列表 | 外部修改不会破坏对象内部数据 |
| 16 | `ValidateBeforeMutateDemo` | 修改一半才发现参数错误 | 全部校验通过后一次更新 | 失败后对象仍保持原状态 |

## 第三阶段：方法契约与边界 `contracts`

| 顺序 | 类 | 直接写法的问题 | 改进方式 | 观察结果 |
|---|---|---|---|---|
| 17 | `BoundaryNormalizationDemo` | 各处重复处理空格和大小写 | 在输入边界统一规范化 | 内部只处理一种标准数据 |
| 18 | `FlagArgumentDemo` | boolean 参数隐藏方法真正执行的操作 | 拆成有业务名称的方法 | 调用代码不再需要猜 true 的含义 |
| 19 | `OperationResultDemo` | 使用 `null` 表达失败，原因不明确 | 返回明确的成功或失败结果 | 调用方能完整处理不同结果 |
| 20 | `ExceptionBoundaryDemo` | 每层捕获、包装甚至吞掉异常 | 保留原因，在最外层统一转换 | 内部原因和外部错误职责分开 |
| 21 | `ModelBoundaryDemo` | 输入、领域和输出共用同一个类 | 每个边界使用自己的模型 | 修改接口字段不再直接污染业务对象 |
| 22 | `CommandQuerySeparationDemo` | 一个方法同时修改状态并拼查询结果 | 修改和查询分别表达 | 调用方可清楚选择写操作或读操作 |
| 23 | `NarrowInterfaceDemo` | 只读代码依赖完整可写仓储 | 只依赖 `OrderReader` | 查询代码无法误调用保存方法 |
| 24 | `TypedDataCarrierDemo` | `Map<String, Object>` 的键和值不安全 | 使用有类型的 record | 字段由编译器检查且含义明确 |

## 第四阶段：依赖、职责与组织 `structure`

| 顺序 | 类 | 直接写法的问题 | 改进方式 | 观察结果 |
|---|---|---|---|---|
| 25 | `ExplicitDependencyDemo` | 方法内部固定创建 Repository | 从构造器传入依赖 | 依赖可以替换并从外部观察 |
| 26 | `ControllableTimeDemo` | 业务代码直接读取系统时间 | 注入 `Clock` | 同一固定时钟总能产生相同时间 |
| 27 | `OwnedStateDemo` | 静态可变状态被所有实例共享 | 由实例拥有自己的状态 | 两个实例的数据互不影响 |
| 28 | `PureCoreDemo` | 计算和保存混合 | 纯计算与外部操作分开 | 计算不再依赖 Repository |
| 29 | `CompositionReuseDemo` | 多层继承才能组合能力 | 使用几个小组件组合计算 | 两种写法得到相同应付金额 |
| 30 | `CentralizedRuleDemo` | 阈值和规则散落在流程里 | 使用一个规则对象集中管理 | 修改规则时只有一个明确入口 |
| 31 | `FeatureCohesionDemo` | 订单代码散落在各种 util/service | 围绕订单功能集中组织 | 两种组织方式产生相同结果 |

`FeatureCohesionDemo` 用内部类模拟包结构。放到真实项目时，重点是把同一个功能放在一起：

```text
按技术名称散落                  按功能内聚
util/OrderMoneyUtils            features/order/model
service/OrderValidator          features/order/application
dto/OrderResponseFormatter      features/order/api
```

## 运行方式

1. 在 IDEA 中展开 `features/codedesign`，按表格顺序选择一个 Demo。
2. 运行该类的 `main` 方法，不需要启动 `PlaygroundApplication`。
3. 对照控制台中的直接写法、改进写法和风险提示阅读代码。

普通示例应输出相同的业务结果并正常退出；专门展示风险的示例会额外输出非法状态、共享数据、
不可控时间或被拒绝的操作。

## 模块边界

- `basics` 学习 Java 语法、集合、异步和并发等基础能力。
- `codedesign` 学习如何用这些能力写出边界清楚、容易修改的代码。
- 后续 `designpatterns` 再单独学习工厂、策略、观察者等设计模式。
- Redis 锁、Mongo 乐观锁、PostgreSQL 防超卖、RocketMQ、Sentinel 等分布式案例继续由现有 feature 演示。
