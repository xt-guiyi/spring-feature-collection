## 工程结构

项目现在是一个 Maven 多模块、三个可启动服务的 Spring Cloud Alibaba 学习工程：

```text
spring-feature-collection/
├── shared/       公共 jar，不启动；只放通用返回对象、异常、校验和工具
├── playground/   学习功能服务，端口 4379
├── user/         用户数据服务，端口 4380，数据库为 user_db
└── gateway/      WebFlux API 网关，端口 8080
```

`gateway` 不依赖 `shared`，避免 WebFlux 网关加载 shared 中的 WebMVC；`playground` 和 `user`
仍按需使用 shared。Playground 内部继续按 `features/{feature}` 组织 Java 基础（`basics`）、Flowable、
Elasticsearch、MongoDB、Redis、RocketMQ、Drools、XXL-JOB、Flyway 和 PostgreSQL 学习代码。

## 微服务组件

- Spring Boot 4.0.0
- Spring Cloud 2025.1.0
- Spring Cloud Alibaba 2025.1.0.0
- Java 21
- Nacos：注册中心和配置中心，客户端端口 8848，控制台端口 18848
- Gateway WebFlux、OpenFeign、LoadBalancer、Sentinel

当前只完成服务发现、配置、网关路由和用户服务调用；暂不引入 Security、Seata、Dubbo、链路追踪、
注册中心集群或 Kubernetes。

## Playground 目录结构

```text
playground/src/main/java/com/xt/xiaoxingxing/playground/
├── PlaygroundApplication.java
├── client/user/                 # OpenFeign 学习示例：调用一次 user-service
├── config/                      # Playground 数据源、MyBatis、Redis、CORS 等服务配置
├── constants/                   # 服务级常量
└── features/
    └── {feature}/               # drools、elasticsearch、flowable、migration、mongo 等
        ├── controller/
        ├── service/
        ├── repository/mapper/
        ├── entity/document/
        ├── dto/request/response/
        ├── vo/                  # 仅在确有查询投影或内部计算需要时使用
        └── constants/enums/
```

不新增 assembler，也不强制做 DTO → VO → DTO 的重复转换。Controller 使用接口 DTO，内部只有在
确实需要投影或计算时才使用 VO。

## 用户数据边界

`user-service` 独占 `user_db.users`，通过以下接口提供用户查询：

```text
GET  /internal/users/{id}
POST /internal/users/batch
```

Playground 只保留一个独立的 OpenFeign 学习示例：
`GET /api/playground/feign/users/{id}`，它通过服务发现调用上面的 User 接口。
Mongo、Flowable 和 RocketMQ 仍按原学习代码使用 Playground 自己的 `demo.users`，不调用
`user-service`；Playground 的 PostgreSQL 学习接口也继续保留 `demo.users` 和 JOIN 示例。
因此这些 feature 可以在 Playground 内独立学习，Feign 示例仅用于演示服务间调用。

## Nacos 配置

服务本地的 `application.yaml` 只保存应用名、默认 `dev` Profile、Nacos 地址和必需的配置导入。
配置模板位于：

```text
docker/nacos/config/application.properties
docker/nacos/config/gateway-service-dev.properties
docker/nacos/config/playground-service-dev.properties
docker/nacos/config/user-service-dev.properties
```

配置使用 `public` namespace 和 `SPRING_FEATURE_COLLECTION` group。启动 Nacos 后执行：

```bash
bash docker/nacos/config/publish-config.sh
```

数据库密码、外部中间件地址和 XXL-JOB token 使用环境变量提供，不写入服务配置模板。

## 本地运行

1. 启动基础设施：`docker compose -f docker/docker-compose.yml up -d nacos postgres`。
2. 如果 PostgreSQL 使用的是已有数据卷，手工执行 `CREATE DATABASE user_db;`；不要执行
   `docker compose down -v`，否则会删除已有学习数据。
3. 发布 Nacos 配置，并为服务设置 `USER_DB_PASSWORD`、`PLAYGROUND_DB_PASSWORD`、Redis、Mongo、
   Elasticsearch、RocketMQ 等环境变量。
4. 分别启动 `UserApplication`、`PlaygroundApplication` 和 `GatewayApplication`。

通过网关访问：

```text
http://localhost:8080/api/users
http://localhost:8080/api/playground/...
```

网关只配置 `/api/users`、`/api/users/**` 和 `/api/playground/**` 两组显式路由，不转发
`/internal/**` 或 `/actuator/**`。Playground 的服务内 CORS 默认关闭，避免经过网关时重复写入
跨域响应头；如需浏览器直连 `4379`，可单独覆盖 `application.cors.enabled=true`。

## Flyway

- `user` 的 `db/migration/V1__create_users.sql` 只在 `user_db` 创建用户表并准备 ID 1–12 的开发用户。
- `playground` 的迁移仍指向 `demo` 库的 `playgroundDataSource` 和既有 `flyway_migration` schema。
- 不修改已经执行过的 Playground Flyway 历史版本。
