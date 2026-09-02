# 数据库初始化脚本目录

目录按“数据库类型 → 数据库名 → 框架或业务 → 脚本文件”组织：

```text
init/
├── mysql/
│   └── xxl_job/                 # MySQL 数据库
│       └── xxl-job/             # XXL-JOB 官方表和框架初始化数据
│           ├── schema/
│           │   └── schema.sql   # 只建表
│           └── data/
│               └── initial-data.sql # 只插入数据
└── postgres/
    ├── schema/
    │   └── create-databases.sql # 没有固定目标数据库的脚本
    ├── demo/                    # demo 数据库：业务表和 Flowable 框架表
    │   ├── schema/              # 业务建表脚本
    │   ├── data/                # 业务数据脚本
    │   └── flowable/            # demo 数据库中的 Flowable 官方系统表
    │       ├── schema/          # Flowable 建表脚本
    │       └── data/            # Flowable 初始化数据
    ├── user_db/                 # user_db 预留目录；当前表由 Flyway 管理
```

判断规则：

- `mysql/xxl_job/xxl-job` 放 MySQL 中 XXL-JOB 官方表及框架自身需要的初始化数据。
- `postgres/demo/flowable` 放 demo 数据库中的 Flowable 官方系统表。
- `postgres/demo` 放本项目业务表，例如用户、订单、请假业务表，以及 XXL-JOB 执行器侧的业务支撑表。
- `postgres/user_db` 当前不放手工 SQL，用户服务的表由 Flyway 迁移脚本创建。
- 文件名统一使用小写 kebab-case，直接表达用途；建表脚本统一放在 `schema`，数据脚本统一放在 `data`。
