package com.xt.xiaoxingxing.playground.xxljob.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 一条稳定业务执行链当前所处的状态。 */
@Getter
public enum XxlLearningExecutionStatus {
    RUNNING("RUNNING"), SUCCESS("SUCCESS"), FAILED("FAILED");

    @EnumValue
    private final String value;

    XxlLearningExecutionStatus(String value) {
        this.value = value;
    }
}
