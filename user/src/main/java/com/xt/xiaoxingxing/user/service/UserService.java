package com.xt.xiaoxingxing.user.service;

import com.xt.xiaoxingxing.user.dto.request.UserCreateRequest;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;

import java.util.Collection;
import java.util.List;

public interface UserService {

    UserResponse create(UserCreateRequest user);

    UserResponse getById(Long id);

    /** 内部服务查询：找不到时返回 null，由调用方决定业务语义。 */
    UserResponse findById(Long id);

    List<UserResponse> list();

    List<UserResponse> findByIds(Collection<Long> ids);
}
