package com.xt.xiaoxingxing.user.repository;

import com.xt.xiaoxingxing.user.entity.User;
import com.xt.xiaoxingxing.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final UserMapper userMapper;

    public int insert(User user) {
        return userMapper.insert(user);
    }

    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    public List<User> findAll() {
        return userMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery()
                        .orderByAsc(User::getId));
    }

    public List<User> findByIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : userMapper.selectBatchIds(ids);
    }
}
