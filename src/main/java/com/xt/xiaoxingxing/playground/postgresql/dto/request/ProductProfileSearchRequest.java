package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import lombok.Data;

/**
 * JSONB 动态查询条件，所有字段都可以省略。
 *
 * <p>只暴露明确业务条件，不允许前端直接传 SQL 或任意 JSONPath，避免动态 SQL 注入。</p>
 */
@Data
public class ProductProfileSearchRequest {

    /** 使用 attributes ->> 'brand' 提取文本后等值查询。 */
    private String brand;

    /** 使用 @> 判断 tags 数组是否包含指定标签。 */
    private String tag;

    /** 使用 jsonb_exists 判断 JSON 顶层是否存在指定 key。 */
    private String requiredKey;

    /** 使用 #>> 取得 warranty.enabled 嵌套值后转换为 boolean。 */
    private Boolean warrantyEnabled;
}
