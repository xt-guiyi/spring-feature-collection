# PostgreSQL JSONB 商品扩展信息 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. This checkout does not authorize subagent dispatch. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 PostgreSQL playground 中实现一套带默认数据、JSONB TypeHandler、原生运算符、索引以及 MyBatis/MyBatis-Plus 双入口的商品扩展信息学习案例。

**Architecture:** 新建独立 `product_profiles` 表和聚焦 JSONB 的 Controller/Service/Mapper，不修改已有五张表实体及旧接口。普通 MyBatis 使用 XML 原生 SQL；MyBatis-Plus 使用 `BaseMapper`、`QueryWrapper.apply` 和 `UpdateWrapper.setSql`，两套入口共享实体、DTO 和业务契约。

**Tech Stack:** Java 21、Spring Boot 4.1、PostgreSQL 18、MyBatis、MyBatis-Plus 3.5.17、Jackson `JsonNode`、PostgreSQL JDBC `PGobject`。

## Global Constraints

- 永远使用中文学习型注释，复杂 Service 方法先列完整步骤，再按“第1步、第2步……”实现。
- 明确区分“PostgreSQL 语法不能原样用于 MySQL”和“MySQL 完全做不了”，禁止不准确的绝对比较。
- MySQL 8.4 的 `JSON_CONTAINS`、`JSON_SET`、`JSON_REMOVE`、函数索引和 JSON 数组多值索引必须在对应注释中说明。
- 不编写或运行测试，不运行 Maven 构建；只进行静态检查。
- 不提交、不清理当前脏工作区，不修改无关文件。

---

### Task 1: 建表、索引和默认 JSONB 数据

**Files:**
- Modify: `docs/schema-demo.sql`

**Produces:** `product_profiles` 表、三个商品扩展文档、GIN 索引和品牌表达式索引。

- [ ] **Step 1: 在旧表清理顺序中加入新表**

在 `products` 之前执行：

```sql
DROP TABLE IF EXISTS product_profiles CASCADE;
```

- [ ] **Step 2: 创建表和约束**

```sql
CREATE TABLE product_profiles (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_profile_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object')
);
```

- [ ] **Step 3: 创建两类索引**

```sql
CREATE INDEX idx_product_profiles_attributes_gin
    ON product_profiles USING GIN (attributes);
CREATE INDEX idx_product_profiles_brand
    ON product_profiles ((attributes ->> 'brand'));
```

注释说明 PostgreSQL GIN 能直接服务支持的 JSONB 操作符；MySQL InnoDB 没有同名 GIN，但可采用函数索引、生成列索引和 JSON 数组多值索引。

- [ ] **Step 4: 插入三条默认数据**

在 `products` 默认数据之后插入 iPhone、MacBook Pro、AirPods Pro 的 JSONB，覆盖字符串、数字、布尔值、数组和嵌套 `warranty` 对象。

### Task 2: JSONB JDBC 映射、实体和请求 DTO

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/typehandler/PgJsonbTypeHandler.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/entity/PgProductProfile.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/ProductProfileCreateRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/ProductProfileSearchRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/ProductProfileAttributesMergeRequest.java`

**Produces:** `JsonNode` 与 PostgreSQL `jsonb` 的双向映射，以及两套入口共享的请求模型。

- [ ] **Step 1: 实现 TypeHandler**

`PgJsonbTypeHandler extends BaseTypeHandler<JsonNode>`，四个 JDBC 方法必须完整实现：

```java
@Override
public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
        throws SQLException {
    PGobject jsonb = new PGobject();
    jsonb.setType("jsonb");
    jsonb.setValue(write(parameter));
    ps.setObject(i, jsonb);
}
```

读取统一调用私有 `read(String json)`，空数据库值返回 `null`，Jackson 异常包装成 `SQLException`。

- [ ] **Step 2: 实现实体**

```java
@TableName(value = "product_profiles", autoResultMap = true)
public class PgProductProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private JsonNode attributes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 实现 DTO**

创建请求包含 `Long productId`、`JsonNode attributes`；搜索请求包含 `String brand`、`String tag`、`String requiredKey`、`Boolean warrantyEnabled`；合并请求包含 `JsonNode attributes`。业务校验统一放 Service，确保两套实现语义一致。

### Task 3: Mapper 契约和普通 MyBatis XML

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisProductProfileMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgProductProfilePlusMapper.java`
- Create: `src/main/resources/mapper/postgresql/PgMyBatisProductProfileMapper.xml`

**Produces:** 普通 MyBatis JSONB SQL 和 MyBatis-Plus `BaseMapper<PgProductProfile>`。

- [ ] **Step 1: 定义普通 Mapper 方法**

```java
Long insertProfile(PgProductProfile profile);
PgProductProfile selectProfileById(@Param("id") Long id);
List<PgProductProfile> selectAllProfiles();
List<PgProductProfile> selectProfilesByCondition(ProductProfileSearchRequest request);
int mergeAttributes(@Param("id") Long id, @Param("attributes") JsonNode attributes);
int updateWarrantyMonths(@Param("id") Long id, @Param("months") int months);
int deleteAttribute(@Param("id") Long id, @Param("key") String key);
```

- [ ] **Step 2: 建立带 TypeHandler 的 resultMap 和插入 SQL**

XML 使用 `resultMap` 显式映射 `attributes`；插入使用 PostgreSQL `RETURNING id` 和 `typeHandler=...PgJsonbTypeHandler`。

- [ ] **Step 3: 实现动态 JSONB 查询**

```sql
attributes ->> 'brand' = #{brand}
attributes @> jsonb_build_object('tags', jsonb_build_array(#{tag}))
jsonb_exists(attributes, #{requiredKey})
(attributes #>> '{warranty,enabled}')::boolean = #{warrantyEnabled}
```

XML 注释给出 MySQL 8.4 对照：`JSON_UNQUOTE(JSON_EXTRACT())`、`JSON_CONTAINS`、`JSON_CONTAINS_PATH`；说明写法不同而非能力完全缺失。

- [ ] **Step 4: 实现三种局部更新**

```sql
attributes = attributes || #{attributes,typeHandler=...PgJsonbTypeHandler}
attributes = jsonb_set(
    attributes,
    '{warranty}',
    COALESCE(attributes -> 'warranty', '{}'::jsonb)
        || jsonb_build_object('months', #{months}),
    true
)
attributes = attributes - #{key}
```

属性删除的 `WHERE` 增加 `jsonb_exists(attributes, #{key})`。

### Task 4: 独立双入口和统一业务契约

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/PgJsonbService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/AbstractPgJsonbController.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/PgMyBatisJsonbController.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/PgMyBatisPlusJsonbController.java`

**Produces:** 两套相同接口后缀和返回结构。

- [ ] **Step 1: 定义 Service 方法**

```java
Long create(ProductProfileCreateRequest request);
PgProductProfile getById(Long id);
List<PgProductProfile> list();
List<PgProductProfile> search(ProductProfileSearchRequest request);
PgProductProfile mergeAttributes(Long id, ProductProfileAttributesMergeRequest request);
PgProductProfile updateWarrantyMonths(Long id, int months);
PgProductProfile deleteAttribute(Long id, String key);
```

- [ ] **Step 2: 定义共享 Controller 端点**

抽象 Controller 暴露设计文档中的七个端点，统一包装 `Result<T>`，Controller 只做路径、请求体和查询参数转换。

- [ ] **Step 3: 创建两套清晰入口**

```java
@RequestMapping("/api/playground/pg/mybatis/jsonb")
@RequestMapping("/api/playground/pg/mybatis-plus/jsonb")
```

两类分别注入对应实现；通过 `@Qualifier` 消除同接口双 Bean 歧义。

### Task 5: 普通 MyBatis Service 实现

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisJsonbServiceImpl.java`

**Consumes:** `PgMyBatisProductProfileMapper`、`PgMyBatisProductMapper`。

- [ ] **Step 1: 实现创建和查询**

创建流程：校验 productId、校验 attributes 是对象、查询商品存在、构造实体、捕获 `DuplicateKeyException`、执行 `insertProfile`。查询不存在统一抛出 `BusinessException("商品扩展信息不存在")`。

- [ ] **Step 2: 实现搜索**

规范化空字符串后调用 XML 动态 SQL；空请求转换为全空 DTO，按 ID 升序返回。

- [ ] **Step 3: 实现合并、嵌套更新和属性删除**

每个复杂方法先写步骤注释。更新成功后重新查询并返回完整 JSON；删除影响零行时先查询记录，再抛出“属性不存在”。

### Task 6: MyBatis-Plus Service 实现

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusJsonbServiceImpl.java`

**Consumes:** `PgProductProfilePlusMapper`、`PgProductPlusMapper`。

- [ ] **Step 1: 使用 BaseMapper 实现创建和普通查询**

`insert`、`selectById`、`selectList(Wrappers.lambdaQuery().orderByAsc(...))` 展示 TypeHandler 与 BaseMapper 配合。

- [ ] **Step 2: 使用 QueryWrapper.apply 实现 JSONB 搜索**

所有外部值通过占位符绑定：

```java
wrapper.apply(hasText(brand), "attributes ->> 'brand' = {0}", brand);
wrapper.apply(hasText(tag),
        "attributes @> CAST({0} AS jsonb)", tagContainmentJson);
wrapper.apply(hasText(requiredKey), "jsonb_exists(attributes, {0})", requiredKey);
wrapper.apply(warrantyEnabled != null,
        "(attributes #>> '{warranty,enabled}')::boolean = {0}", warrantyEnabled);
```

标签包含 JSON 使用 Jackson 安全生成，禁止字符串拼接用户输入。

- [ ] **Step 3: 使用 UpdateWrapper.setSql 实现局部更新**

合并补丁先序列化成 JSON 字符串，再通过 `CAST({0} AS jsonb)` 绑定；质保月数使用 `jsonb_set + jsonb_build_object`；删除属性同时用 `apply(jsonb_exists...)` 限定 key 存在。

### Task 7: 学习说明和静态检查

**Files:**
- Create: `docs/postgresql-jsonb-learning.md`
- Modify: `docs/superpowers/plans/2026-08-09-pg-jsonb-product-profiles.md`

- [ ] **Step 1: 编写调用说明**

文档覆盖表结构、默认数据、七个接口的请求响应、TypeHandler 数据流、每个 PostgreSQL 运算符的逐层解释、索引与 `EXPLAIN` 使用方式。

- [ ] **Step 2: 编写 MySQL 8.4 对照**

引用 PostgreSQL 与 MySQL 官方文档，明确：PostgreSQL 语法不能原样复制；MySQL 有函数式替代；GIN 与 MySQL 函数/生成列/多值索引模型不同。

- [ ] **Step 3: 执行静态检查**

使用 `rg` 检查 Controller 路径、Service 方法、Mapper/XML statement ID、TypeHandler 引用、JSONB 运算符、MySQL 比较注释和默认数据；使用 `git diff --check` 检查格式。不运行测试或 Maven。

