package com.xt.xiaoxingxing.playground.xxljob.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 批次状态由工作项集合推导，不由某一个分片凭内存猜测。 */
@Getter
public enum XxlLearningBatchStatus {
    READY("READY"), PROCESSING("PROCESSING"), RETRY_WAIT("RETRY_WAIT"), SUCCESS("SUCCESS"),
    PARTIAL_SUCCESS("PARTIAL_SUCCESS"), FAILED("FAILED");

    @EnumValue
    private final String value;

    XxlLearningBatchStatus(String value) {
        this.value = value;
    }
}
