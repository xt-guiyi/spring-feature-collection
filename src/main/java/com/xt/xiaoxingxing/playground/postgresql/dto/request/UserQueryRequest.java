package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 用户动态查询条件。
 *
 * <p>字段均为可选：普通 MyBatis 使用 XML 的 {@code <if>} 按需拼接条件，
 * MyBatis-Plus 使用 LambdaQueryWrapper 的 condition 参数按需追加条件。</p>
 */
@Data
public class UserQueryRequest {

    /** 同时匹配用户名和邮箱的模糊关键字。 */
    private String keyword;

    /** 用户状态精确匹配。 */
    private String status;

    /** 主键 IN 查询集合。 */
    private List<Long> ids;

    /** 为 true 时只查询手机号为 NULL 的用户。 */
    private Boolean phoneIsNull;
}
