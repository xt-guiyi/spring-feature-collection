package com.xt.xiaoxingxing.playground.postgresql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class PgUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String phone;

    private String status;

    private LocalDateTime createdAt;
}
