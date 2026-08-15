package com.xt.xiaoxingxing.playground.postgresql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xt.xiaoxingxing.playground.postgresql.typehandler.PgJsonbTypeHandler;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 商品动态扩展信息。
 *
 * <p>{@code productId} 仍然使用关系字段，方便唯一约束、关联和精确查询；只有不同商品之间
 * 结构不一致的品牌、规格、标签、质保等内容放入 {@code attributes JSONB}。这比把价格、
 * 库存等核心字段也塞进 JSON 更容易保证数据质量。</p>
 */
@Data
@TableName(value = "product_profiles", autoResultMap = true)
public class PgProductProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /**
     * autoResultMap=true 配合字段 TypeHandler，使 BaseMapper 的写入和查询都能完成
     * JsonNode 与 PostgreSQL jsonb 的转换。
     */
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private JsonNode attributes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
