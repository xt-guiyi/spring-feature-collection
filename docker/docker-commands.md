# Docker 常用命令手册

本项目的 Docker Compose 文件位于 `docker/docker-compose.yml`。以下命令默认在项目根目录执行。

## 一、进入 Docker 目录

```bash
cd docker
```

后续命令中的 `docker compose` 会自动读取当前目录下的 `docker-compose.yml` 和 `.env` 文件。

## 二、Compose 常用命令

### 查看配置是否正确

```bash
docker compose config
```

用于检查 YAML 格式、环境变量替换和服务配置，不会启动容器。

### 创建并后台启动所有服务

```bash
docker compose up -d
```

首次启动或镜像发生变化时，Compose 会创建或重新创建对应容器。

### 查看服务状态

```bash
docker compose ps
```

查看容器是否运行、端口映射和健康状态。

### 查看所有服务日志

```bash
docker compose logs
```

持续查看日志：

```bash
docker compose logs -f
```

只查看某个服务：

```bash
docker compose logs -f 服务名
```

### 重启服务

```bash
docker compose restart 服务名
```

重启全部服务：

```bash
docker compose restart
```

### 停止服务

```bash
docker compose stop
```

只停止容器，不删除容器和数据卷。

### 停止并删除容器

```bash
docker compose down
```

会删除 Compose 创建的容器和网络，但默认保留命名卷中的数据。

### 拉取最新镜像

```bash
docker compose pull
```

拉取完成后重新创建服务：

```bash
docker compose up -d
```

## 三、容器常用命令

### 查看正在运行的容器

```bash
docker ps
```

查看所有容器，包括已经停止的容器：

```bash
docker ps -a
```

### 查看容器详细信息

```bash
docker inspect 容器名称
```

### 进入容器

```bash
docker exec -it 容器名称 bash
```

如果镜像没有 `bash`，使用 `sh`：

```bash
docker exec -it 容器名称 sh
```

### 在容器中执行单条命令

```bash
docker exec 容器名称 命令
```

### 查看容器资源占用

```bash
docker stats
```

## 四、镜像常用命令

### 查看本地镜像

```bash
docker images
```

### 删除镜像

```bash
docker rmi 镜像名称:标签
```

删除镜像前，需要先停止并删除依赖该镜像的容器。

### 清理无用镜像

```bash
docker image prune
```

该命令只清理未被使用的悬空镜像，执行前仍应确认提示内容。

## 五、数据卷常用命令

### 查看数据卷

```bash
docker volume ls
```

### 查看数据卷详情

```bash
docker volume inspect 卷名称
```

### 删除数据卷

```bash
docker volume rm 卷名称
```

删除数据卷会删除其中保存的应用数据，必须确认目标卷后再执行。

### 删除 Compose 服务和数据卷

```bash
docker compose down -v
```

这是高风险命令，会删除当前 Compose 使用的命名卷。除非明确需要清空持久化数据，否则不要执行。

## 六、网络常用命令

### 查看 Docker 网络

```bash
docker network ls
```

### 查看网络详情

```bash
docker network inspect 网络名称
```

## 七、常见排错命令

### 查看容器最近日志

```bash
docker logs --tail 200 容器名称
```

### 查看容器端口映射

```bash
docker port 容器名称
```

### 查看容器内部的 DNS 解析

```bash
docker exec 容器名称 getent hosts 服务名
```

### 查看容器环境变量

```bash
docker inspect 容器名称 --format '{{range .Config.Env}}{{println .}}{{end}}'
```

注意：该命令可能显示密码等敏感信息，不要将输出粘贴到公共日志或提交到 Git。

## 八、常用操作建议

```text
修改 docker-compose.yml
        ↓
docker compose config
        ↓
docker compose up -d
        ↓
docker compose ps
        ↓
docker compose logs -f 服务名
```

遇到服务问题时，先确认容器状态、日志、网络和数据卷，再考虑删除容器或数据卷。
