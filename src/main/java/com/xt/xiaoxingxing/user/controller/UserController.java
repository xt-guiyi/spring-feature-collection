package com.xt.xiaoxingxing.user.controller;

import com.xt.xiaoxingxing.shared.common.Result;
import com.xt.xiaoxingxing.user.dto.request.UserCreateRequest;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;
import com.xt.xiaoxingxing.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public Result<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @GetMapping("/{id}")
    public Result<UserResponse> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @GetMapping
    public Result<List<UserResponse>> list() {
        return Result.ok(userService.list());
    }
}
