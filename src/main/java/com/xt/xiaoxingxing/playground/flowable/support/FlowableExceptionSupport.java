package com.xt.xiaoxingxing.playground.flowable.support;

import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.flowable.common.engine.api.FlowableException;

import java.util.function.Supplier;

/** 把 Flowable API 的运行时异常统一转换为项目现有的 BusinessException。 */
public final class FlowableExceptionSupport {

    private FlowableExceptionSupport() {
    }

    public static <T> T call(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (FlowableException exception) {
            throw convert(operation, exception);
        }
    }

    private static BusinessException convert(String operation, FlowableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof BusinessException businessException) {
                return businessException;
            }
            cause = cause.getCause();
        }
        String detail = exception.getMessage();
        String message = detail == null || detail.isBlank()
                ? operation + "失败"
                : operation + "失败：" + detail;
        BusinessException businessException = new BusinessException(message);
        businessException.initCause(exception);
        return businessException;
    }
}
