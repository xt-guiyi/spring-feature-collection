package com.xt.xiaoxingxing.user.repository;

import com.xt.xiaoxingxing.user.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class UserRepository {

    private final Map<Long, User> data = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Long nextId() {
        return idGenerator.getAndIncrement();
    }

    public void save(User user) {
        data.put(user.getId(), user);
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(data.get(id));
    }

    public Map<Long, User> findAll() {
        return data;
    }

    public void deleteById(Long id) {
        data.remove(id);
    }
}
