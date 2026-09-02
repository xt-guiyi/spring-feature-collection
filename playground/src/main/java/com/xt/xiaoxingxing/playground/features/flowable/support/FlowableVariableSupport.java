package com.xt.xiaoxingxing.playground.features.flowable.support;

import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import org.springframework.stereotype.Component;

/** 统一处理 Flowable 流程变量到业务基础类型的转换。 */
@Component
public class FlowableVariableSupport {

    /** 将流程变量转换为 Long 类型。 */
    public Long asLong(Object value, String variableName) {
        BusinessAssert.notNull(value, "流程变量缺少" + variableName);
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException("流程变量" + variableName + "不是有效数字");
        }
    }

    /** 将流程变量转换为 Integer 类型。 */
    public Integer asInteger(Object value, String variableName) {
        BusinessAssert.notNull(value, "流程变量缺少" + variableName);
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException("流程变量" + variableName + "不是有效整数");
        }
    }
}
