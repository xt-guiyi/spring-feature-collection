package com.xt.xiaoxingxing.playground.features.mongo.service.support;

import com.xt.xiaoxingxing.playground.features.mongo.dto.response.UserSummaryResponse;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgUserPlusMapper;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
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
 * MongoDB 学习模块访问 playground 自己的 PostgreSQL 用户表的入口。
 *
 * <p>MongoDB 中只保存 userId，用户摘要从同一个 playground 进程的 demo.users 读取。
 * 列表场景使用批量查询，避免在循环中逐个查询形成 N+1。</p>
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

    /** 批量查询用户；历史数据对应的用户不存在时仍保留原 Mongo 文档。 */
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

    /** 将本地用户实体裁剪成 Mongo 接口原有的用户摘要结构。 */
    public UserSummaryResponse toSummary(PgUser user) {
        if (user == null) {
            return null;
        }
        UserSummaryResponse result = new UserSummaryResponse();
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setEmail(user.getEmail());
        result.setStatus(user.getStatus());
        return result;
    }
}
