# MongoDB 动态问卷学习说明

## 1. 为什么这个案例同时使用 PostgreSQL 和 MongoDB

PostgreSQL `demo.users` 保存结构稳定、需要唯一身份的用户主数据；MongoDB 保存结构灵活的问卷和答卷。
两个数据库不做数据库级 JOIN，也不使用 MongoDB `DBRef`，而是共同保存一个普通的 `userId`，由 Java Service
批量查询并组装。

```text
PostgreSQL users                   MongoDB questionnaires
┌───────────────┐                 ┌─────────────────────────────┐
│ id = 1        │◀── userId ─────│ createdByUserId = 1         │
│ username      │                 │ questions: [ ... ]          │
│ email/status  │                 └─────────────────────────────┘
└───────────────┘
        ▲                         MongoDB questionnaire_submissions
        └──────── userId ─────────│ questionnaireId / answers[] │
                                  └─────────────────────────────┘
```

列表查询不会对每一条 MongoDB 文档分别查询 PostgreSQL。Service 会先收集当前页全部 `userId`，执行一次
`selectBatchIds`，再转换成 `Map<Long, PgUser>` 完成组装。

## 2. 本地连接

项目默认连接：

```text
mongodb://root:123456@localhost:27017/mongo_playground?authSource=admin
```

如需覆盖：

```bash
export MONGODB_URI='mongodb://其他用户:密码@localhost:27017/mongo_playground?authSource=admin'
```

MongoDB collection 会在首次插入时创建。项目开启了注解索引自动创建，适合本地学习；生产环境应通过受版本
控制的脚本或迁移工具显式管理索引。

PostgreSQL 示例用户来自 `docs/schema-demo.sql`，下面示例默认使用 `userId = 1`。

## 3. 推荐调用顺序

所有接口统一位于 `/api/playground/mongo`。

### 3.1 创建草稿

```http
POST /api/playground/mongo/questionnaires
Content-Type: application/json
```

```json
{
  "title": "Java 技术调查",
  "description": "学习 MongoDB 动态问卷",
  "createdByUserId": 1
}
```

记住返回的问卷 `id` 和 `version`。新文档的 `version` 通常从 `0` 开始，每次修改成功都会加一；后续接口必须
传递最新的 `expectedVersion`。

### 3.2 添加五种题目

文本题：

```http
POST /api/playground/mongo/questionnaires/{id}/questions?expectedVersion=0
```

```json
{
  "title": "请介绍你的 Java 学习经历",
  "type": "TEXT",
  "required": true,
  "settings": {
    "maxLength": 500
  }
}
```

单选题：

```json
{
  "title": "你最常用的数据库是？",
  "type": "SINGLE_CHOICE",
  "required": true,
  "settings": {
    "options": ["PostgreSQL", "MySQL", "MongoDB"]
  }
}
```

多选题：

```json
{
  "title": "你掌握哪些中间件？",
  "type": "MULTIPLE_CHOICE",
  "required": true,
  "settings": {
    "options": ["Redis", "MongoDB", "Kafka", "Elasticsearch"],
    "minSelections": 1,
    "maxSelections": 3
  }
}
```

数字题：

```json
{
  "title": "你的开发年限？",
  "type": "NUMBER",
  "required": false,
  "settings": {
    "min": 0,
    "max": 50
  }
}
```

评分题：

```json
{
  "title": "你对 Java 的兴趣评分？",
  "type": "RATING",
  "required": true,
  "settings": {
    "min": 1,
    "max": 5
  }
}
```

每次添加后都从响应里取得新 `version` 和生成的题目 `id`，不要继续使用旧版本。

### 3.3 更新和删除内嵌题目

```http
PUT /api/playground/mongo/questionnaires/{id}/questions/{questionId}?expectedVersion={当前版本}
DELETE /api/playground/mongo/questionnaires/{id}/questions/{questionId}?expectedVersion={当前版本}
```

Java 对应的 MongoDB 运算符分别是位置运算符 `$` 和 `$pull`。它们只修改命中的数组元素，不需要把整个问卷
读取到 Java 后覆盖整个 `questions` 数组。

### 3.4 发布问卷

```http
POST /api/playground/mongo/questionnaires/{id}/publish?expectedVersion={当前版本}
```

空问卷不能发布。发布后题目结构固定，只能继续查询、提交答卷，或者把问卷关闭。

### 3.5 提交答卷

先通过问卷详情取得真实的 `questionId`：

```http
POST /api/playground/mongo/questionnaires/{id}/submissions
Content-Type: application/json
```

```json
{
  "userId": 1,
  "answers": [
    {
      "questionId": "文本题UUID",
      "value": "我正在学习 Spring Data MongoDB"
    },
    {
      "questionId": "单选题UUID",
      "value": "PostgreSQL"
    },
    {
      "questionId": "多选题UUID",
      "value": ["Redis", "MongoDB"]
    },
    {
      "questionId": "数字题UUID",
      "value": 3.5
    },
    {
      "questionId": "评分题UUID",
      "value": 5
    }
  ]
}
```

Service 会把单选答案也规范化成单元素数组，使单选和多选能共用 `$unwind` 聚合逻辑。NUMBER 保存为
`BigDecimal/Decimal128`，RATING 保存为整数。

同一个用户对同一个问卷只能提交一次。Repository 的 `existsBy...` 用于提前提示，真正的并发正确性由
`(questionnaireId,userId)` 唯一复合索引保证。

### 3.6 查询与统计

```http
GET /api/playground/mongo/questionnaires?keyword=Java&status=PUBLISHED&pageNum=1&pageSize=10
GET /api/playground/mongo/questionnaires/{id}/submissions?pageNum=1&pageSize=10
GET /api/playground/mongo/users/1/submissions?pageNum=1&pageSize=10
GET /api/playground/mongo/questionnaires/{id}/statistics
```

统计接口包含：

- 总答卷数；
- 每道题的作答人数；
- 单选和多选的选项次数；
- NUMBER、RATING 的平均值、最小值和最大值；
- 没有人作答的题目仍然返回，计数为 `0`。

## 4. Repository 和 MongoTemplate 的分工

```text
MongoRepository
├── insert 完整问卷/答卷文档
├── findById
├── existsByQuestionnaireIdAndUserId
└── countByQuestionnaireId

MongoTemplate
├── 动态 Criteria 查询
├── sort + skip + limit 分页
├── $push / questions.$ / $pull 数组更新
├── status + version 条件状态迁移
└── $unwind / $group / $avg / $min / $max 聚合
```

这不是两套入口重复实现同一功能，而是让两种 API 各自负责更自然的部分。

## 5. 等价 Mongo Shell 示例

动态查询：

```javascript
db.questionnaires.find({
  status: "PUBLISHED",
  $or: [
    {title: /Java/i},
    {description: /Java/i}
  ]
}).sort({updatedAt: -1, _id: -1}).skip(0).limit(10)
```

向数组追加题目并使用版本条件防止并发覆盖：

```javascript
db.questionnaires.updateOne(
  {_id: ObjectId("问卷ID"), status: "DRAFT", version: 0},
  {
    $push: {questions: {/* 题目文档 */}},
    $inc: {version: 1},
    $currentDate: {updatedAt: true}
  }
)
```

修改指定数组元素：

```javascript
db.questionnaires.updateOne(
  {
    _id: ObjectId("问卷ID"),
    status: "DRAFT",
    version: 1,
    "questions.id": "题目UUID"
  },
  {
    $set: {"questions.$": {/* 新题目文档，保留原UUID */}},
    $inc: {version: 1}
  }
)
```

选择题选项统计的核心管道：

```javascript
db.questionnaire_submissions.aggregate([
  {$match: {questionnaireId: "问卷ID"}},
  {$unwind: "$answers"},
  {$match: {"answers.questionType": {$in: ["SINGLE_CHOICE", "MULTIPLE_CHOICE"]}}},
  {$unwind: "$answers.value"},
  {
    $group: {
      _id: {
        questionId: "$answers.questionId",
        option: "$answers.value"
      },
      total: {$sum: 1}
    }
  }
])
```

## 6. 一致性边界

- PostgreSQL 与 MongoDB 之间没有外键，也没有分布式事务。
- 创建问卷和提交答卷时先读取 PostgreSQL 用户，再写 MongoDB。
- 本学习模块不提供删除 PostgreSQL 用户的接口；如果历史用户缺失，查询仍返回 MongoDB 文档，用户摘要为
  `null`。
- 问卷发布与答卷提交涉及两个 MongoDB collection，本版本不要求本地 MongoDB 配置副本集事务。
- `expectedVersion` 解决的是问卷单文档并发修改；唯一索引解决的是并发重复提交，两者用途不同。
