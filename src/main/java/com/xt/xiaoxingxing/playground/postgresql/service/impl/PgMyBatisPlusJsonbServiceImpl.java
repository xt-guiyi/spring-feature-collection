package com.xt.xiaoxingxing.playground.postgresql.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.ProductProfileAttributesMergeRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.ProductProfileCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.ProductProfileSearchRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProductProfile;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgProductPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgProductProfilePlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.service.PgJsonbService;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MyBatis-Plus JSONB 业务实现。
 *
 * <p>BaseMapper 负责标准 CRUD；PostgreSQL JSONB 没有对应的 Lambda 方法，因此通过官方
 * QueryWrapper.apply 和 UpdateWrapper.setSql 使用原生表达式。所有外部值仍使用占位参数绑定，
 * 不能为了“链式写法”直接拼接进 SQL。</p>
 */
@Service
@RequiredArgsConstructor
public class PgMyBatisPlusJsonbServiceImpl implements PgJsonbService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PgProductProfilePlusMapper profileMapper;
    private final PgProductPlusMapper productMapper;

    @Override
    public Long create(ProductProfileCreateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验请求、商品ID和 JSON 根节点。
         * 2. 使用 PgProductPlusMapper.selectById 校验关系表商品。
         * 3. 使用 BaseMapper.insert 插入，字段 TypeHandler 自动把 JsonNode 转成 jsonb。
         * 4. 通过实体主键回填取得 id，并转换 product_id 唯一约束异常。
         */

        // 第1步：两套 Service 使用相同规则，保证接口可以直接对照。
        BusinessAssert.notNull(request, "创建请求不能为空");
        validatePositiveId(request.getProductId(), "商品ID");
        validateJsonObject(request.getAttributes(), "attributes必须是JSON对象");

        // 第2步：JSONB 不会替代关系完整性校验；productId 仍然是明确的关系字段。
        BusinessAssert.notNull(productMapper.selectById(request.getProductId()), "商品不存在");

        // 第3步：@TableName(autoResultMap=true) 让 BaseMapper 使用字段上的 PgJsonbTypeHandler。
        PgProductProfile profile = new PgProductProfile();
        profile.setProductId(request.getProductId());
        profile.setAttributes(request.getAttributes());
        try {
            profileMapper.insert(profile);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("该商品已经存在扩展信息");
        }

        // 第4步：IdType.AUTO 会把 BIGSERIAL 生成的主键回填到实体。
        return profile.getId();
    }

    @Override
    public PgProductProfile getById(Long id) {
        validatePositiveId(id, "扩展信息ID");
        return BusinessAssert.notNull(profileMapper.selectById(id), "商品扩展信息不存在");
    }

    @Override
    public List<PgProductProfile> list() {
        return profileMapper.selectList(Wrappers.<PgProductProfile>lambdaQuery()
                .orderByAsc(PgProductProfile::getId));
    }

    @Override
    public List<PgProductProfile> search(ProductProfileSearchRequest request) {
        /*
         * 实现步骤：
         * 1. 规范化四个可选查询条件。
         * 2. 为 tag 构造合法的 {"tags":[...]} JSON，禁止手工拼接用户字符串。
         * 3. 使用 apply 加入 PostgreSQL ->>、@>、jsonb_exists、#>> 条件。
         * 4. 使用固定字段 id 排序并执行一次数据库查询。
         */

        // 第1步：空请求代表无筛选条件，与普通 MyBatis XML 行为一致。
        ProductProfileSearchRequest normalized = normalizeSearch(request);
        QueryWrapper<PgProductProfile> wrapper = Wrappers.query();

        // 第2步：Jackson 会正确转义引号、反斜杠等字符，再把完整 JSON 作为一个参数绑定。
        String tagContainmentJson = BusinessAssert.hasText(normalized.getTag())
                ? buildTagContainmentJson(normalized.getTag())
                : null;

        // 第3步：SQL 片段全部固定；{0} 是 MyBatis-Plus 参数占位，不是字符串格式化拼接。
        wrapper.apply(BusinessAssert.hasText(normalized.getBrand()),
                        "attributes ->> 'brand' = {0}", normalized.getBrand())
                .apply(BusinessAssert.hasText(normalized.getTag()),
                        "attributes @> CAST({0} AS jsonb)", tagContainmentJson)
                .apply(BusinessAssert.hasText(normalized.getRequiredKey()),
                        "jsonb_exists(attributes, CAST({0} AS text))", normalized.getRequiredKey())
                .apply(normalized.getWarrantyEnabled() != null,
                        "(attributes #>> '{warranty,enabled}')::boolean = {0}",
                        normalized.getWarrantyEnabled())
                .orderByAsc("id");

        // 第4步：这些表达式仍由 PostgreSQL 执行，Wrapper 只是帮助组织和绑定 SQL。
        return profileMapper.selectList(wrapper);
    }

    @Override
    public PgProductProfile mergeAttributes(Long id, ProductProfileAttributesMergeRequest request) {
        /*
         * 实现步骤：
         * 1. 校验记录ID和对象补丁。
         * 2. 把补丁安全序列化为 JSON 字符串，通过 setSql 的 {0} 参数绑定。
         * 3. PostgreSQL 把参数 CAST 为 jsonb 后执行 || 合并，再返回最新数据。
         */

        // 第1步：只允许对象补丁，避免数组与对象合并产生难以理解的业务结构。
        validatePositiveId(id, "扩展信息ID");
        BusinessAssert.notNull(request, "合并请求不能为空");
        validateJsonObject(request.getAttributes(), "attributes补丁必须是JSON对象");

        // 第2步：JsonNode 不直接拼入 SQL；序列化文本仍通过 PreparedStatement 参数发送。
        String patchJson = writeJson(request.getAttributes());
        UpdateWrapper<PgProductProfile> wrapper = Wrappers.update();
        wrapper.eq("id", id)
                .setSql("attributes = attributes || CAST({0} AS jsonb)", patchJson)
                .setSql("updated_at = CURRENT_TIMESTAMP");

        // 第3步：MySQL 不能原样使用 ||，可用 JSON_MERGE_PATCH 完成相近对象合并。
        BusinessAssert.affected(profileMapper.update(null, wrapper), "商品扩展信息不存在");
        return getById(id);
    }

    @Override
    public PgProductProfile updateWarrantyMonths(Long id, int months) {
        /*
         * 实现步骤：
         * 1. 校验记录ID和质保月数。
         * 2. 使用 setSql 绑定 months，让 jsonb_set 在数据库中完成嵌套局部更新。
         * 3. 根据影响行数判断记录存在，再返回数据库最终 JSON。
         */

        // 第1步：0表示无质保，负数属于非法业务数据。
        validatePositiveId(id, "扩展信息ID");
        BusinessAssert.isTrue(months >= 0, "质保月数不能小于0");

        // 第2步：MySQL 有 JSON_SET，但不能原样使用 PostgreSQL 的 jsonb_set 和 text[] 路径。
        UpdateWrapper<PgProductProfile> wrapper = Wrappers.update();
        wrapper.eq("id", id)
                .setSql("""
                        attributes = jsonb_set(
                            attributes,
                            '{warranty}',
                            COALESCE(
                                CASE
                                    WHEN jsonb_typeof(attributes -> 'warranty') = 'object'
                                        THEN attributes -> 'warranty'
                                END,
                                '{}'::jsonb
                            )
                                || jsonb_build_object('months', {0}),
                            true
                        )
                        """, months)
                .setSql("updated_at = CURRENT_TIMESTAMP");

        // 第3步：重新查询还能直接观察 warranty.enabled 没有被 months 更新覆盖。
        BusinessAssert.affected(profileMapper.update(null, wrapper), "商品扩展信息不存在");
        return getById(id);
    }

    @Override
    public PgProductProfile deleteAttribute(Long id, String key) {
        /*
         * 实现步骤：
         * 1. 校验记录ID和顶层属性名。
         * 2. 用 apply 限定 key 存在，再用 setSql 执行 JSONB - text。
         * 3. 影响0行时重新查询，区分记录不存在与属性不存在。
         */

        // 第1步：key 永远作为参数绑定，不能用字符串拼接构造“attributes - 某个key”。
        validatePositiveId(id, "扩展信息ID");
        BusinessAssert.hasText(key, "JSON属性名不能为空");
        String normalizedKey = key.trim();

        // 第2步：MySQL 的对应函数是 JSON_REMOVE；PostgreSQL - 操作符不能原样迁移。
        UpdateWrapper<PgProductProfile> wrapper = Wrappers.update();
        wrapper.eq("id", id)
                .apply("jsonb_exists(attributes, CAST({0} AS text))", normalizedKey)
                .setSql("attributes = attributes - CAST({0} AS text)", normalizedKey)
                .setSql("updated_at = CURRENT_TIMESTAMP");
        int affectedRows = profileMapper.update(null, wrapper);
        if (affectedRows > 0) {
            return getById(id);
        }

        // 第3步：记录存在但 key 不存在时返回更精确提示。
        getById(id);
        throw new BusinessException("JSON属性不存在: " + normalizedKey);
    }

    private ProductProfileSearchRequest normalizeSearch(ProductProfileSearchRequest source) {
        ProductProfileSearchRequest result = new ProductProfileSearchRequest();
        if (source == null) {
            return result;
        }
        result.setBrand(trimToNull(source.getBrand()));
        result.setTag(trimToNull(source.getTag()));
        result.setRequiredKey(trimToNull(source.getRequiredKey()));
        result.setWarrantyEnabled(source.getWarrantyEnabled());
        return result;
    }

    private String buildTagContainmentJson(String tag) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ArrayNode tags = root.putArray("tags");
        tags.add(tag);
        return writeJson(root);
    }

    private String writeJson(JsonNode value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON序列化失败");
        }
    }

    private void validatePositiveId(Long id, String fieldName) {
        BusinessAssert.isTrue(id != null && id > 0, fieldName + "必须大于0");
    }

    private void validateJsonObject(JsonNode value, String message) {
        BusinessAssert.isTrue(value != null && value.isObject(), message);
    }

    private String trimToNull(String value) {
        return BusinessAssert.hasText(value) ? value.trim() : null;
    }
}
