package com.xt.xiaoxingxing.user;

import com.xt.xiaoxingxing.shared.web.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;

/** user 用户服务启动入口。 */
@SpringBootApplication(scanBasePackages = "com.xt.xiaoxingxing.user")
@EnableDiscoveryClient
@MapperScan("com.xt.xiaoxingxing.user.mapper")
@Import(GlobalExceptionHandler.class)
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
