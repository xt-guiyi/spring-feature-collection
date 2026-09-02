package com.xt.xiaoxingxing.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum UserStatus {

    ACTIVE("ACTIVE", "正常"),
    INACTIVE("INACTIVE", "禁用"),
    DELETED("DELETED", "已删除");

    @EnumValue
    private final String value;
    private final String desc;

    UserStatus(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
