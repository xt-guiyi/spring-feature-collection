package com.xt.xiaoxingxing.playground.mongo.service.support;

import com.xt.xiaoxingxing.playground.mongo.vo.UserSummaryVO;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgUserPlusMapper;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MongoDB 模块访问 PostgreSQL 用户表的唯一入口。
 *
 * <p>MongoDB 中只保存 userId，用户名称、邮箱和状态始终从 PostgreSQL 读取。列表场景必须
 * 使用 selectBatchIds 一次查齐，禁止在循环中逐个 selectById 形成 N+1。</p>
 */
@Component
@RequiredArgsConstructor
public class PostgresUserLookup {

    private static final String ACTIVE = "ACTIVE";

    private final PgUserPlusMapper userMapper;

    /** 创建问卷和提交答卷都要求用户真实存在且处于 ACTIVE 状态。 */
    public PgUser requireActive(Long userId) {
        BusinessAssert.isTrue(userId != null && userId > 0, "用户ID必须大于0");
        PgUser user = BusinessAssert.notNull(userMapper.selectById(userId), "PostgreSQL用户不存在");
        BusinessAssert.isTrue(ACTIVE.equalsIgnoreCase(user.getStatus()), "PostgreSQL用户不是ACTIVE状态");
        return user;
    }

    /**
     * 批量查询用户。历史答卷对应的用户即使已经不存在，也不会导致整个答卷被过滤。
     */
    public Map<Long, PgUser> findMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PgUser> users = userMapper.selectBatchIds(distinctIds);
        return users.stream().collect(Collectors.toMap(
                PgUser::getId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    public UserSummaryVO toSummary(PgUser user) {
        if (user == null) {
            return null;
        }
        UserSummaryVO result = new UserSummaryVO();
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setEmail(user.getEmail());
        result.setStatus(user.getStatus());
        return result;
    }
}
