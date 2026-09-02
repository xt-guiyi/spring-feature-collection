package com.xt.xiaoxingxing.playground.features.openfeign.controller;

import com.xt.xiaoxingxing.shared.feign.user.client.UserClient;
import com.xt.xiaoxingxing.shared.feign.user.dto.UserRemoteResponse;
import com.xt.xiaoxingxing.shared.core.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OpenFeign 学习示例：通过共享客户端和服务发现调用 user-service。 */
@RestController
@RequestMapping("/api/playground/feign/users")
@RequiredArgsConstructor
public class UserFeignDemoController {

    private final UserClient userClient;

    /** 通过 user-service 的内部接口查询一个用户。 */
    @GetMapping("/{id}")
    public Result<UserRemoteResponse> getById(@PathVariable("id") Long id) {
        return userClient.getById(id);
    }
}
