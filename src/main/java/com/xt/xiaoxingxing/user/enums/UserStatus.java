package com.xt.xiaoxingxing.user.enums;

import lombok.Getter;

@Getter
public enum UserStatus {

    ACTIVE("正常"),
    INACTIVE("禁用"),
    DELETED("已删除");

    private final String desc;

    UserStatus(String desc) {
        this.desc = desc;
    }
}
