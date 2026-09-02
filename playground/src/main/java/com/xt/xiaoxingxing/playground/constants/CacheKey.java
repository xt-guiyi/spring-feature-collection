package com.xt.xiaoxingxing.playground.constants;

public final class CacheKey {

    private CacheKey() {
    }

    public static final String USER_PREFIX = "user:";

    public static String userKey(Long userId) {
        return USER_PREFIX + userId;
    }
}
