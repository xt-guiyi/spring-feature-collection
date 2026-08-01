package com.xt.xiaoxingxing.user.service;

import com.xt.xiaoxingxing.user.dto.request.UserCreateRequest;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse create(UserCreateRequest request);

    UserResponse getById(Long id);

    List<UserResponse> list();
}
