package com.xt.xiaoxingxing.shared.feign.user.config;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import com.xt.xiaoxingxing.shared.feign.user.fallback.UserClientFallbackFactory;

/** user-service Feign 客户端的共享配置。 */
public class UserClientConfiguration {

    /** 注册共享降级工厂，避免每个业务模块重复声明。 */
    @Bean
    public UserClientFallbackFactory userClientFallbackFactory() {
        return new UserClientFallbackFactory();
    }

    /** 显式设置 user-service Feign 客户端的连接和读取超时。 */
    @Bean
    public Request.Options userClientRequestOptions(
            @Value("${spring.cloud.openfeign.client.config.user-service.connectTimeout:3000}") int connectTimeout,
            @Value("${spring.cloud.openfeign.client.config.user-service.readTimeout:5000}") int readTimeout) {
        return new Request.Options(connectTimeout, readTimeout, true);
    }
}
