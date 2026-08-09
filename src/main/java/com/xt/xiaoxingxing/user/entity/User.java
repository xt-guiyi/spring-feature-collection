package com.xt.xiaoxingxing.user.entity;

import com.xt.xiaoxingxing.user.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户领域实体。
 * <p>
 * 当前使用内存仓库 {@link com.xt.xiaoxingxing.user.repository.UserRepository} 存储，
 * 后续如需接入数据库，可直接替换为 MyBatis-Plus 实体。
 */
@Data
public class User {

    private Long id;

    private String username;

    private String email;

    private UserStatus status;

    private LocalDateTime createTime;
}
