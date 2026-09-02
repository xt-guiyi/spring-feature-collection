package com.xt.xiaoxingxing.user.controller;

import com.xt.xiaoxingxing.shared.core.response.Result;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;
import com.xt.xiaoxingxing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 仅供服务间调用的用户查询接口，不配置到网关路由。 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserResponse> getById(@PathVariable("id") Long id) throws InterruptedException {
        Thread.sleep(3000);
        return Result.ok(userService.findById(id));
    }

    @PostMapping("/batch")
    public Result<List<UserResponse>> findByIds(@RequestBody(required = false) List<Long> ids) {
        return Result.ok(userService.findByIds(ids));
    }
}
