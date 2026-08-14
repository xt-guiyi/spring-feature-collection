package com.xt.xiaoxingxing.playground.xxljob.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 单个分片工作项的持久状态。 */
@Getter
public enum XxlLearningWorkItemStatus {
    PENDING("PENDING"), RUNNING("RUNNING"), RETRY_WAIT("RETRY_WAIT"), SUCCESS("SUCCESS"), DEAD("DEAD");

    @EnumValue
    private final String value;

    XxlLearningWorkItemStatus(String value) {
        this.value = value;
    }
}
