package com.xt.xiaoxingxing.shared.feign.user.exception;

import com.xt.xiaoxingxing.shared.core.exception.BusinessException;

/** user-service 不可访问时使用的明确业务异常。 */
public class UserServiceUnavailableException extends BusinessException {

    public UserServiceUnavailableException(Throwable cause) {
        super("用户服务不可用，请稍后重试");
        if (cause != null) {
            initCause(cause);
        }
    }
}
