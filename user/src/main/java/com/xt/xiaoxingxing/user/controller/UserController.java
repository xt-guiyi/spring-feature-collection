package com.xt.xiaoxingxing.user.controller;

import com.xt.xiaoxingxing.shared.core.response.Result;
import com.xt.xiaoxingxing.user.dto.request.UserCreateRequest;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;
import com.xt.xiaoxingxing.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 用户服务的用户接口统一由此 Controller 承载，服务间调用也复用同一组路径。 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/users")
    public Result<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @GetMapping("/api/users/{id}")
    public Result<UserResponse> getById(@PathVariable Long id) throws InterruptedException {
        // 测试响应超时
//        Thread.sleep(6000);
        return Result.ok(userService.getById(id));
    }

    @GetMapping("/api/users")
    public Result<List<UserResponse>> list() {
        return Result.ok(userService.list());
    }

    @PostMapping("/api/users/batch")
    public Result<List<UserResponse>> findByIds(
            @RequestBody(required = false) List<Long> ids) {
        return Result.ok(userService.findByIds(ids));
    }

}
