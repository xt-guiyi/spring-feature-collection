package com.xt.xiaoxingxing.playground.features.postgresql.service.impl;

import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgProductProfile;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisProductMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisProductProfileMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.service.PgJsonbService;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileAttributesMergeRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileCreateRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductProfileSearchRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.response.PgProductProfileResponse;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** 普通 MyBatis JSONB 业务实现：复杂 JSONB 操作全部在 Mapper XML 中完成。 */
@Service
@RequiredArgsConstructor
public class PgMyBatisJsonbServiceImpl implements PgJsonbService {

    private final PgMyBatisProductProfileMapper profileMapper;
    private final PgMyBatisProductMapper productMapper;

    @Override
    public Long create(ProductProfileCreateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验请求、商品ID和 attributes 根节点类型。
         * 2. 查询关系表 products，保证 JSONB 扩展信息关联到真实商品。
         * 3. 构造实体，通过 XML TypeHandler 写入 jsonb，并用 RETURNING 取得主键。
         * 4. 把 product_id 唯一约束异常转换成明确的重复创建提示。
         */

        // 第1步：JSONB 能保存数组和标量，但本业务约定根节点必须是对象，便于按 key 查询和合并。
        BusinessAssert.notNull(request, "创建请求不能为空");
        validatePositiveId(request.getProductId(), "商品ID");
        validateJsonObject(request.getAttributes(), "attributes必须是JSON对象");

        // 第2步：product_id 是逻辑外键，因此必须由 Service 主动完成存在性校验。
        BusinessAssert.notNull(productMapper.selectProductById(request.getProductId()), "商品不存在");

        // 第3步：PgJsonbTypeHandler 会把 JsonNode 包装为 PGobject(jsonb)，前端不需要接触 PGobject。
        PgProductProfile profile = toEntity(request);

        // 第4步：提前查询不能替代唯一约束；并发创建时仍以数据库约束作为最终兜底。
        try {
            return profileMapper.insertProfile(profile);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("该商品已经存在扩展信息");
        }
    }

    @Override
    public PgProductProfileResponse getById(Long id) {
        validatePositiveId(id, "扩展信息ID");
        return toResponse(BusinessAssert.notNull(profileMapper.selectProfileById(id), "商品扩展信息不存在"));
    }

    @Override
    public List<PgProductProfileResponse> list() {
        return profileMapper.selectAllProfiles().stream().map(this::toResponse).toList();
    }

    @Override
    public List<PgProductProfileResponse> search(ProductProfileSearchRequest request) {
        /*
         * 实现步骤：
         * 1. 把 null 请求转换为空条件对象，允许学习接口查询全部数据。
         * 2. 对字符串条件 trim，并把空白字符串转为 null。
         * 3. 交给 XML <if> 动态拼装 ->>、@>、jsonb_exists 和 #>> 条件。
         */

        // 第1、2步：不直接修改 Controller 传入对象，避免调用方继续使用时看到参数被改变。
        ProductProfileSearchRequest normalized = normalizeSearch(request);

        // 第3步：筛选、JSONB 运算和排序由数据库一次完成，不把所有 JSON 拉回 Java 再过滤。
        return profileMapper.selectProfilesByCondition(normalized).stream().map(this::toResponse).toList();
    }

    @Override
    public PgProductProfileResponse mergeAttributes(Long id, ProductProfileAttributesMergeRequest request) {
        /*
         * 实现步骤：
         * 1. 校验记录ID和补丁必须是 JSON 对象。
         * 2. 使用 PostgreSQL JSONB || 在数据库内合并顶层属性。
         * 3. 根据影响行数判断记录是否存在，再查询并返回合并后的完整文档。
         */

        // 第1步：数组补丁没有明确的顶层 key 覆盖语义，因此本案例只接受对象。
        validatePositiveId(id, "扩展信息ID");
        BusinessAssert.notNull(request, "合并请求不能为空");
        validateJsonObject(request.getAttributes(), "attributes补丁必须是JSON对象");

        // 第2步：|| 是 PostgreSQL JSONB 操作符；MySQL 可用 JSON_MERGE_PATCH，但语法不能原样照搬。
        int affectedRows = profileMapper.mergeAttributes(id, request.getAttributes());

        // 第3步：UPDATE 匹配不到记录时影响0行，统一转为“商品扩展信息不存在”。
        BusinessAssert.affected(affectedRows, "商品扩展信息不存在");
        return getById(id);
    }

    @Override
    public PgProductProfileResponse updateWarrantyMonths(Long id, int months) {
        /*
         * 实现步骤：
         * 1. 校验记录ID和质保月数。
         * 2. 使用 jsonb_set 只修改 warranty 子对象中的 months。
         * 3. 重新查询，展示 warranty.enabled 等其他字段仍然存在。
         */

        // 第1步：允许0表示无质保，但不允许负数。
        validatePositiveId(id, "扩展信息ID");
        BusinessAssert.isTrue(months >= 0, "质保月数不能小于0");

        // 第2步：SQL 会先合并已有 warranty 对象，父对象不存在时再创建，避免整段覆盖。
        BusinessAssert.affected(profileMapper.updateWarrantyMonths(id, months), "商品扩展信息不存在");

        // 第3步：返回数据库最终值，而不是在 Java 中猜测局部更新后的 JSON。
        return getById(id);
    }

    @Override
    public PgProductProfileResponse deleteAttribute(Long id, String key) {
        /*
         * 实现步骤：
         * 1. 校验记录ID和要删除的顶层 key。
         * 2. 使用 JSONB - text 删除属性，WHERE 同时要求该 key 存在。
         * 3. 影响0行时重新查询，区分“记录不存在”和“属性不存在”。
         */

        // 第1步：key 只作为绑定参数传入，不允许作为 SQL 字符串直接拼接。
        validatePositiveId(id, "扩展信息ID");
        BusinessAssert.hasText(key, "JSON属性名不能为空");
        String normalizedKey = key.trim();

        // 第2步：PostgreSQL 用 - 删除顶层 key；MySQL 可用 JSON_REMOVE，路径语法不同。
        int affectedRows = profileMapper.deleteAttribute(id, normalizedKey);
        if (affectedRows > 0) {
            return getById(id);
        }

        // 第3步：getById 会先给出“记录不存在”；记录存在时再给出精确的属性错误。
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

    private void validatePositiveId(Long id, String fieldName) {
        BusinessAssert.isTrue(id != null && id > 0, fieldName + "必须大于0");
    }

    private void validateJsonObject(JsonNode value, String message) {
        BusinessAssert.isTrue(value != null && value.isObject(), message);
    }

    private String trimToNull(String value) {
        return BusinessAssert.hasText(value) ? value.trim() : null;
    }

    private PgProductProfile toEntity(ProductProfileCreateRequest source) {
        PgProductProfile target = new PgProductProfile();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private PgProductProfileResponse toResponse(PgProductProfile source) {
        PgProductProfileResponse target = new PgProductProfileResponse();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
