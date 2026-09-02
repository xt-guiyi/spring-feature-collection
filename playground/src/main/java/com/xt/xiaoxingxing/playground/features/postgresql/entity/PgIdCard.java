package com.xt.xiaoxingxing.playground.features.postgresql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("id_cards")
public class PgIdCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String cardNumber;

    private String realName;
}
