package com.xt.xiaoxingxing.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xt.xiaoxingxing.user.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户服务持久化实体，映射 user_db.users 表。
 */
@Data
@TableName("users")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private UserStatus status;

    @TableField("create_time")
    private LocalDateTime createTime;
}
