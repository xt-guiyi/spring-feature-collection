package com.xt.xiaoxingxing.user.service.impl;

import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import com.xt.xiaoxingxing.user.dto.request.UserCreateRequest;
import com.xt.xiaoxingxing.user.dto.response.UserResponse;
import com.xt.xiaoxingxing.user.entity.User;
import com.xt.xiaoxingxing.user.enums.UserStatus;
import com.xt.xiaoxingxing.user.repository.UserRepository;
import com.xt.xiaoxingxing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse create(UserCreateRequest source) {
        User user = new User();
        user.setUsername(source.getUsername());
        user.setEmail(source.getEmail());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreateTime(LocalDateTime.now());
        BusinessAssert.affected(userRepository.insert(user), "用户创建失败");
        return toResponse(user);
    }

    @Override
    public UserResponse getById(Long id) {
        User user = BusinessAssert.notNull(userRepository.findById(id), "用户不存在");
        return toResponse(user);
    }

    @Override
    public UserResponse findById(Long id) {
        return toResponse(userRepository.findById(id));
    }

    @Override
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<UserResponse> findByIds(Collection<Long> ids) {
        return userRepository.findByIds(ids).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserResponse toResponse(User source) {
        if (source == null) {
            return null;
        }
        UserResponse target = new UserResponse();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setEmail(source.getEmail());
        target.setStatus(source.getStatus());
        target.setCreateTime(source.getCreateTime());
        return target;
    }
}
