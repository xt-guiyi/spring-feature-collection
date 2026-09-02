package com.xt.xiaoxingxing.shared.feign.user.client;

import com.xt.xiaoxingxing.shared.core.response.Result;
import com.xt.xiaoxingxing.shared.feign.user.config.UserClientConfiguration;
import com.xt.xiaoxingxing.shared.feign.user.dto.UserRemoteResponse;
import com.xt.xiaoxingxing.shared.feign.user.fallback.UserClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** user-service 的共享 OpenFeign 客户端，通过服务发现访问用户服务。 */
@FeignClient(
        name = "user-service",
        contextId = "userClient",
        configuration = UserClientConfiguration.class,
        fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    /** 通过 user-service 的内部接口查询用户。 */
    @GetMapping("/internal/users/{id}")
    Result<UserRemoteResponse> getById(@PathVariable("id") Long id);
}
