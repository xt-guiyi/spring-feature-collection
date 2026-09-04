# Java 设计模式学习模块

本模块用订单、支付、库存、退款、促销和履约等后端业务讲解 23 个 GoF 设计模式。
每个类都包含一个极短的直接写法和一个标准模式写法，可以独立运行 `main`，不需要启动
`PlaygroundApplication` 或任何外部中间件。

设计模式不是必须套用的模板。先确认代码确实存在相应的变化、组合或协作问题，再接受模式带来的
额外类型和间接调用；只有一个固定实现时，直接代码通常更合适。

## 源码分类

```text
designpatterns/
├── creational/   # 创建型：怎样创建对象
├── structural/   # 结构型：怎样组合对象
└── behavioral/   # 行为型：对象怎样协作
```

## 第一阶段：后端高频核心

| 顺序 | 类 | 业务问题与核心角色 | 预期输出 | 适用与克制 |
|---|---|---|---|---|
| 1 | `FactoryMethodPatternDemo` | `Creator` 子类决定创建哪种 `PaymentGateway` | 钱包结果与直接写法相同，并扩展银行卡渠道 | 创建流程相同、产品可变时使用；只有一个固定实现时不必使用 |
| 2 | `BuilderPatternDemo` | `Builder` 分步骤组装并校验不可变订单查询 | 暴露长构造器传反页码的风险，并拒绝非法金额区间 | 可选字段很多时使用；两三个必填参数直接构造即可 |
| 3 | `AdapterPatternDemo` | `Adapter` 将遗留物流接口、单位和状态码转换为内部模型 | 两种写法报价相同，并展示公斤误当克的风险 | 接口不兼容时使用；接口已经一致时不要增加转换层 |
| 4 | `CompositePatternDemo` | `Product` 叶子和 `Bundle` 组合都实现 `PriceComponent` | 同一 `price()` 计算单品和嵌套套餐 | 业务天然是树结构时使用；固定的一层列表无需组合模式 |
| 5 | `DecoratorPatternDemo` | 多个 `QuoteDecorator` 保持报价接口并逐层委托 | 商品价依次叠加运费、保价和礼盒费用 | 需要动态组合增强时使用；固定步骤直接方法更清楚 |
| 6 | `FacadePatternDemo` | `CheckoutFacade` 为库存、支付、订单子系统提供统一结算入口 | 正常下单结果一致，库存失败时不会调用支付 | 客户端不应了解子系统顺序时使用；不要把所有业务都塞进外观 |
| 7 | `ProxyPatternDemo` | `PermissionRefundProxy` 与真实退款服务实现相同接口 | 有权限退款成功，无权限请求不会到达真实服务 | 需要访问控制或延迟访问时使用；单纯增加业务步骤不属于代理 |
| 8 | `ChainOfResponsibilityPatternDemo` | 地址、库存、额度、风控 Handler 组成可中止校验链 | 正常订单结果一致，高风险订单在对应节点停止 | 校验节点经常增减时使用；固定的两个判断无需建链 |
| 9 | `ObserverPatternDemo` | 订单 Subject 向积分、消息、审计 Observer 一对多通知 | 三个监听器收到相同支付事件 | 单 JVM 内一对多通知时使用；它不替代 MQ 的可靠投递 |
| 10 | `StatePatternDemo` | 售后 Context 把状态特有行为委托给 `RefundState` | 待审核变为通过，通过后拒绝操作被阻止 | 各状态行为复杂时使用；只有迁移关系时优先迁移表 |
| 11 | `StrategyPatternDemo` | Context 可替换普通、VIP、偏远地区运费算法 | 普通结果一致，并可运行时切换另外两种算法 | 同一目标有多个算法时使用；简单数据映射优先决策表 |
| 12 | `TemplateMethodPatternDemo` | 抽象履约流程固定骨架，子类实现变化步骤 | 实物结果一致，虚拟商品复用相同步骤顺序 | 骨架稳定且允许继承时使用；仅拆私有步骤不是模板方法 |

## 第二阶段：低频但应该理解

| 顺序 | 类 | 业务问题与核心角色 | 预期输出 | 适用与克制 |
|---|---|---|---|---|
| 13 | `AbstractFactoryPatternDemo` | Factory 创建同一渠道配套的支付、退款产品族 | 直接拼装发生渠道错配，两套产品族内部保持一致 | 多种产品必须成套切换时使用；只创建一个产品用工厂方法 |
| 14 | `PrototypePatternDemo` | Prototype 从促销模板深复制渠道和可变规则 | 浅复制会修改原模板，深复制的容器与规则对象均不共享 | 复制已有复杂对象时使用；简单对象直接构造更直观 |
| 15 | `BridgePatternDemo` | Report 与 Renderer 分离报表类型、输出格式两个维度 | 订单/结算报表可分别组合 JSON/CSV | 两个维度独立变化时使用；只有一个变化轴用普通组合即可 |
| 16 | `CommandPatternDemo` | 可执行 Command 持有订单服务 Receiver，由 Invoker 排队执行 | 执行前无变化，执行后依次支付、发货并留下记录 | 需要排队、记录或延迟执行时使用；不要把普通请求 DTO 叫命令模式 |
| 17 | `IteratorPatternDemo` | Iterator 隐藏订单分页源和翻页状态 | `for-each` 连续读完订单，只取三条时仅加载两页 | 遍历机制复杂且应隐藏时使用；普通 List 直接遍历即可 |
| 18 | `MediatorPatternDemo` | 订单、支付、库存组件通过中介者协调取消流程 | 退款、释放库存、返还优惠券的结果与直接调用链一致 | 同级组件形成网状依赖时使用；注意中介者可能膨胀 |
| 19 | `MementoPatternDemo` | Originator 创建不透明快照，Caretaker 只负责保存 | 编辑校验失败后恢复原草稿，外部无法修改内部列表 | 确实需要撤销或恢复时使用；完整历史数据通常仍要持久化方案 |

## 第三阶段：理解并谨慎使用

| 顺序 | 类 | 业务问题与核心角色 | 预期输出 | 适用与克制 |
|---|---|---|---|---|
| 20 | `SingletonPatternDemo` | 枚举 Singleton 共享只读货币精度目录 | 两个调用方在当前应用类加载器内得到同一实例，并查到 CNY 精度 2 | 不跨类加载器和进程，也不要保存可变业务状态 |
| 21 | `FlyweightPatternDemo` | Factory 按 SKU 复用不可变元数据，成交价和数量留在订单明细 | 六条明细从六个元数据实例降为两个，总额不变 | 大量重复小对象时使用；共享对象必须不可变并控制缓存增长 |
| 22 | `InterpreterPatternDemo` | Expression 组合解释 `VIP AND amount >= 100` | VIP 大额订单为 true，普通会员为 false | 只适合很小且稳定的语法；复杂 DSL 使用解析器或规则引擎 |
| 23 | `VisitorPatternDemo` | Element 接受运费和发票 Visitor，完成双分派 | 运费与直接写法一致，并新增发票展示操作 | 元素类型稳定、操作常增加时使用；元素类型常变时维护成本很高 |

## 容易混淆的模式

| 对比 | 区别 |
|---|---|
| State 与[迁移表状态机](../codedesign/controlflow/StateMachineDemo.java) | State 把状态特有行为放进对象；迁移表集中表达“状态 + 事件 → 下一状态” |
| Strategy 与[决策表](../codedesign/controlflow/DecisionTableDemo.java) | Strategy 替换整套算法；决策表集中维护条件和结果数据 |
| Builder 与[参数对象](../codedesign/modeling/ParameterObjectDemo.java) | 参数对象把输入整体传递；Builder 分步骤构造并在最后校验对象 |
| Command 与[参数对象](../codedesign/modeling/ParameterObjectDemo.java) | GoF Command 持有 Receiver 并提供 `execute()`；`CreateOrderCommand` 只是输入数据 |
| Command 与[修改查询分离](../codedesign/contracts/CommandQuerySeparationDemo.java) | Command 是可执行对象；代码级 CQS 只是把写方法和读方法分开 |
| Decorator 与责任链 | Decorator 通常每一层都委托并增强；责任链可以在某个节点终止 |
| Decorator 与[普通组合](../codedesign/structure/CompositionReuseDemo.java) | Decorator 保持同一接口并逐层包裹；普通组合没有这个结构要求 |
| Adapter 与 Facade | Adapter 改变不兼容接口；Facade 简化一组已经存在的接口 |
| Facade 与 Mediator | Facade 面向外部提供入口；Mediator 管理内部同级组件的协作 |
| Factory Method 与简单工厂 | Factory Method 由 Creator 子类覆盖创建方法；静态 `switch` 只是简单工厂 |

## 当前项目中的相似落点

- [ArticleDocument](../elasticsearch/document/ArticleDocument.java) 使用 Lombok Builder 构造搜索文档。
- [PgJsonbTypeHandler](../postgresql/typehandler/PgJsonbTypeHandler.java) 在 JDBC 类型与项目 JSON 类型之间转换，具有 Adapter 的职责。
- [OrderStatisticsListener](../rocketmq/listener/OrderStatisticsListener.java) 展示事件订阅的业务形态，但它是 MQ 消费者，不是本模块的单 JVM Observer。
- [UserClient](../../../../../../../../../../shared/src/main/java/com/xt/xiaoxingxing/shared/feign/user/client/UserClient.java) 的实现由 OpenFeign 运行时生成，可帮助理解框架代理；本模块使用更直观的静态 Proxy。

## 运行方式

1. 在 IDEA 中按上面的学习顺序打开任意 `*PatternDemo`。
2. 运行该类的 `main`，无需启动服务或准备基础设施。
3. 先看直接写法的问题，再识别模式角色、协作过程和扩展结果。

所有示例只演示模式结构，不代表生产系统的事务、并发、持久化或分布式可靠性已经解决。
