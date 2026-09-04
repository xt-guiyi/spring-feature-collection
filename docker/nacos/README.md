# Nacos 本地配置

Compose 中的 `nacos` 使用 standalone 模式：

- 控制台：<http://127.0.0.1:18848/nacos>
- 客户端 API：`127.0.0.1:8848`
- gRPC：`127.0.0.1:9848`、`127.0.0.1:9849`

配置模板位于 `config/`，统一使用 `public` namespace 和
`SPRING_FEATURE_COLLECTION` group，配置格式为 YAML。启动 Nacos 后，在项目根目录执行：

```bash
bash docker/nacos/config/publish-config.sh
```

这会发布以下 dataId：

```text
application.yaml
gateway-service-dev.yaml
playground-service-dev.yaml
user-service-dev.yaml
playground-service-sentinel-flow-rules.json
```

数据库密码、外部中间件地址和 XXL-JOB token 通过运行服务时的环境变量提供，模板不保存真实凭据。

PostgreSQL 的数据库和表初始化改为人工执行，不再由 Compose 自动挂载到
`/docker-entrypoint-initdb.d/`。启动 PostgreSQL 后，在项目根目录按顺序执行：

```bash
docker exec -i postgres psql -U root -d postgres < docker/init/postgres/schema/create-databases.sql
docker exec -i postgres psql -U root -d demo < docker/init/postgres/demo/schema/user-business-schema.sql
docker exec -i postgres psql -U root -d demo < docker/init/postgres/demo/data/user-business-data.sql
docker exec -i postgres psql -U root -d demo < docker/init/postgres/demo/schema/xxl-job-execution-schema.sql
docker exec -i postgres psql -U root -d demo < docker/init/postgres/demo/flowable/schema/schema.sql
docker exec -i postgres psql -U root -d demo < docker/init/postgres/demo/flowable/data/data.sql
docker exec -i postgres psql -U root -d demo < docker/init/postgres/demo/schema/flowable-leave-business-schema.sql
```

最后启动 user 服务，让 Flyway 在 `user_db` 中创建用户表和开发用户。

不要为了创建 `user_db` 执行 `docker compose down -v`，这会删除现有学习数据。
