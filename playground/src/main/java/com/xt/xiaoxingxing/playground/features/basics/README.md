# Java 基础学习模块

本模块不提供 HTTP 接口。每个主题只有一个可独立运行的 `main` 类，直接在 IDEA 中运行即可，
无需启动 Playground 服务或外部中间件。

## 学习顺序

| 顺序 | 类 | 学习内容 | 观察结果 |
|---|---|---|---|
| 1 | `ObjectOrientedDemo` | 封装、继承、多态、接口、record | 父类引用调用子类重写方法，接口引用调用学习行为 |
| 2 | `GenericsDemo` | 泛型类、泛型方法、上下界通配符 | 类型安全地保存、读取和汇总数据 |
| 3 | `CollectionsDemo` | List、Set、Map、Deque、PriorityQueue | 对比有序、去重、键值、队列和优先级场景 |
| 4 | `StreamOptionalDemo` | Lambda、过滤、转换、分组、汇总、Optional | 从课程集合得到分组与统计结果 |
| 5 | `StringDemo` | 不可变性、equals、StringBuilder | 原字符串保持不变并完成内容比较和字符串拼接 |
| 6 | `BigDecimalDemo` | 字符串构造、精确计算、compareTo、舍入 | 得到稳定的金额计算结果 |
| 7 | `DateTimeDemo` | 本地时间、时间戳、时区、格式化、Duration | 完成时间转换并计算时间差 |
| 8 | `ExceptionDemo` | 捕获、抛出、自定义异常、finally | 观察异常处理和 finally 的执行顺序 |
| 9 | `IoNioDemo` | Path、Files、文本读写、资源清理 | 写入并读取临时文件，最后删除文件 |
| 10 | `AnnotationReflectionDemo` | 运行时注解、反射读取与调用 | 找到带注解的方法并执行 |
| 11 | `JdkAsyncDemo` | Thread、ExecutorService/Future、CompletableFuture、虚拟线程 | 观察调用线程、执行线程和异步结果 |
| 12 | `SpringAsyncDemo` | @EnableAsync、@Async、命名线程池、Spring 代理 | 任务在指定线程池中执行，容器随后关闭 |
| 13 | `ConcurrencySafetyDemo` | 竞态条件、synchronized、Lock、AtomicInteger | 不安全计数发生丢失更新，安全方案结果正确 |

## 运行方式

1. 在 IDEA 中打开任意 Demo 类。
2. 运行该类的 `main` 方法。
3. 对照控制台输出和类内注释理解结果。

建议按表格顺序学习；各类互不依赖，也可以单独运行。

## 模块边界

`basics` 只讲单 JVM 内的 Java 基础。分布式与持久化并发案例继续查看现有模块：

- Redis：Redisson 分布式锁。
- MongoDB：版本字段与乐观锁。
- PostgreSQL：原子扣减库存与固定加锁顺序。
