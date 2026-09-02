# Elasticsearch Java Client 索引管理基本用法

`client.indices()` 用于管理索引、Mapping、Alias 和刷新操作。下面示例中的 `client` 是注入的 `ElasticsearchClient`。

```java
private final ElasticsearchClient client;
```

这些方法通常会抛出 `IOException`，业务代码需要处理或继续向上抛出。

## 两种请求写法

简单请求通常使用 Lambda：

```java
client.indices().create(request -> request.index("article_search_v1"));
```

需要按条件组装请求时，可以使用 Builder：

```java
CreateIndexRequest request = new CreateIndexRequest.Builder()
        .index("article_search_v1")
        .build();

client.indices().create(request);
```

两种写法最终调用的是同一个创建索引接口。

## 1. 创建索引

同时设置分片数量和字段 Mapping。

```java
var response = client.indices().create(request -> request
        .index("article_search_v1")
        .settings(settings -> settings
                .numberOfShards("1")
                .numberOfReplicas("0"))
        .mappings(mapping -> mapping
                .properties("title", field -> field.text(text -> text))));

boolean acknowledged = response.acknowledged();
```

## 2. 删除索引

删除索引以及索引中的全部文档。

```java
var response = client.indices().delete(request -> request
        .index("article_search_v1"));

boolean acknowledged = response.acknowledged();
```

## 3. 判断索引是否存在

```java
boolean exists = client.indices().exists(request -> request
        .index("article_search_v1"))
        .value();
```

## 4. 查询索引

返回索引的 Settings、Mapping 和 Alias 等信息。

```java
var response = client.indices().get(request -> request
        .index("article_search_v1"));

var indexState = response.indices().get("article_search_v1");
```

## 5. 查询 Mapping

```java
var response = client.indices().getMapping(request -> request
        .index("article_search_v1"));

var mapping = response.get("article_search_v1").mappings();
var fields = mapping.properties();
```

## 6. 修改 Mapping

下面是在已有索引中增加一个 `author` 字段。

```java
var response = client.indices().putMapping(request -> request
        .index("article_search_v1")
        .properties("author", field -> field.keyword(keyword -> keyword)));

boolean acknowledged = response.acknowledged();
```

已有字段的类型通常不能直接修改，需要创建新索引后迁移数据。

## 7. 查询 Alias

查询 Alias 当前指向哪些物理索引。

```java
var response = client.indices().getAlias(request -> request
        .name("article_search"));

var indexNames = response.aliases().keySet();
```

## 8. 添加、删除或切换 Alias

下面把 `article_search` 从 `v1` 原子切换到 `v2`。

```java
var response = client.indices().updateAliases(request -> request
        .actions(action -> action.remove(remove -> remove
                .index("article_search_v1")
                .alias("article_search")))
        .actions(action -> action.add(add -> add
                .index("article_search_v2")
                .alias("article_search")
                .isWriteIndex(true))));

boolean acknowledged = response.acknowledged();
```

同一个请求中的多个 Alias 操作会一次性应用。

## 9. 刷新索引

刷新后，刚写入的文档可以立即被搜索到。

```java
client.indices().refresh(request -> request
        .index("article_search_v1"));
```

频繁手动刷新会影响写入性能，主要用于本地练习或确实需要立即搜索的场景。

## 快速对照

| 方法 | 作用 |
| --- | --- |
| `create(...)` | 创建索引 |
| `delete(...)` | 删除索引 |
| `exists(...)` | 判断索引是否存在 |
| `get(...)` | 查询索引完整信息 |
| `getMapping(...)` | 查询 Mapping |
| `putMapping(...)` | 增加或更新允许修改的 Mapping |
| `getAlias(...)` | 查询 Alias 指向 |
| `updateAliases(...)` | 添加、删除或切换 Alias |
| `refresh(...)` | 刷新索引，使新数据可被搜索 |
