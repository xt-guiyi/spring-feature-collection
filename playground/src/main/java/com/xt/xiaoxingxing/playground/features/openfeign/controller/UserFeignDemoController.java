package com.xt.xiaoxingxing.playground.features.openfeign.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
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

    /** Sentinel 限流测试接口，不调用下游服务，便于单独观察限流效果。 nacos 持久化配置*/
    @GetMapping("/limit-test")
    @SentinelResource("playground-user-rate-limit-test")
    public Result<String> limitTest() {
        return Result.ok("限流测试接口调用成功");
    }

    /** Sentinel 熔断测试接口：通过主动抛出异常模拟用户服务调用失败。 sentinel 可视化网站配置， 非持久化， 网站关掉就没了，但是通常不会关 */
    @GetMapping("/circuit-breaker-test")
    @SentinelResource("playground-user-circuit-breaker-test")
    public Result<String> circuitBreakerTest() {
        throw new IllegalStateException("模拟用户服务异常");
    }
}
