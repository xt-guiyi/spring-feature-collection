package com.xt.xiaoxingxing.playground.features.flowable.support;

import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisUserMapper;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 只保留需要跨 Delegate/Service 复用的本地用户校验。 */
@Component
@RequiredArgsConstructor
public class FlowableUserSupport {

    private final PgMyBatisUserMapper userMapper;

    /** 校验用户存在且未被标记为禁用。 */
    public void requireActiveUser(Long userId, String role) {
        BusinessAssert.notNull(userId, role + "用户ID不能为空");
        PgUser user = BusinessAssert.notNull(userMapper.selectUserById(userId), role + "用户不存在");
        BusinessAssert.isTrue("ACTIVE".equalsIgnoreCase(user.getStatus()), role + "用户不是ACTIVE状态");
    }

}
