package com.xt.xiaoxingxing.shared.feign.user.fallback;

import com.xt.xiaoxingxing.shared.core.response.Result;
import com.xt.xiaoxingxing.shared.feign.user.client.UserClient;
import com.xt.xiaoxingxing.shared.feign.user.dto.UserRemoteResponse;
import com.xt.xiaoxingxing.shared.feign.user.exception.UserServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;

/** user-service 的共享 Feign 降级处理。 */
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public Result<UserRemoteResponse> getById(Long id) {
                throw new UserServiceUnavailableException(cause);
            }
        };
    }
}
