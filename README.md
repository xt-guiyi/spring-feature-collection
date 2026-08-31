## 技术栈

- Java 21
- Spring Boot 4.1.0
- Maven
- PostgreSQL / Flyway
- Redis
- Lombok
- Jakarta Validation

## 目录结构说明

```text
src/main/java/com/xt/xiaoxingxing/
├── XiaoxingxingApplication.java    # 启动类
├── shared/                          # 公共/基础设施代码
│   ├── common/                      # 统一返回结果、分页结果等通用对象
│   ├── config/                      # 全局配置类（CORS、Redis 等）
│   ├── constants/                   # 常量
│   ├── enums/                       # 全局枚举
│   ├── exception/                   # 业务异常、全局异常处理
│   ├── util/                        # 工具类
│   └── aspect/                      # AOP 切面（预留）
└── {business-module}/               # 业务模块，不同模块内部结构一致
    ├── controller/                  # API 接口层
    ├── service/                     # 业务逻辑层
    │   └── impl/                    # 业务实现层
    ├── repository/                  # 数据访问层
    ├── entity/                      # 数据库实体
    ├── dto/                         # 数据传输对象
    │   ├── request/                 # 请求参数
    │   └── response/                # 响应数据
    ├── vo/                          # 视图对象（可选）
    └── enums/                       # 模块内枚举
```

## 模块入口

- [Flyway 数据库迁移模块](src/main/java/com/xt/xiaoxingxing/playground/migration/README.md)：在现有 `demo` 库的 `flyway_migration` schema 中演示 V1→V5 版本化迁移。

## 配置文件

```text
src/main/resources/
├── application.yaml         # 公共配置
├── application-dev.yaml     # 开发环境配置
├── application-prod.yaml    # 生产环境配置
└── logback-spring.xml       # 日志配置
```

## 运行方式

1. 确保本地 Redis 已启动（默认 `localhost:6379`）
2. 在 IDEA 中右键 `pom.xml` → `Maven → Reload Project`
3. 运行配置中 **Active profiles** 填入 `dev`
4. 运行 `XiaoxingxingApplication`

## 接口文档

项目已集成 Swagger，启动后访问：

```text
http://localhost:4379/swagger-ui.html
```
