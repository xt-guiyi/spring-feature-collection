package com.xt.xiaoxingxing.user.service.impl;

import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.user.dto.request.UserCreateRequest;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;
import com.xt.xiaoxingxing.user.entity.User;
import com.xt.xiaoxingxing.user.enums.UserStatus;
import com.xt.xiaoxingxing.user.repository.UserRepository;
import com.xt.xiaoxingxing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse create(UserCreateRequest request) {
        User user = new User();
        user.setId(userRepository.nextId());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        userRepository.save(user);
        return toResponse(user);
    }

    @Override
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return toResponse(user);
    }

    @Override
    public List<UserResponse> list() {
        return userRepository.findAll().values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        BeanUtils.copyProperties(user, response);
        return response;
    }
}
