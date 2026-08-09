package com.xt.xiaoxingxing.playground.redis.controller;

import com.xt.xiaoxingxing.playground.redis.service.LockDemoService;
import com.xt.xiaoxingxing.shared.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playground/redis/lock")
@RequiredArgsConstructor
public class LockDemoController {

    private final LockDemoService lockDemoService;

    /**
     * 对指定资源尝试加锁
     *
     * @param resourceKey 资源标识，如订单号
     * @return 加锁结果
     */
    @GetMapping("/{resourceKey}")
    public Result<String> lock(@PathVariable String resourceKey) {
        return Result.ok(lockDemoService.lockDemo(resourceKey));
    }
}
