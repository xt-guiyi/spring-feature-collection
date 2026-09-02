package com.xt.xiaoxingxing.playground;

import com.xt.xiaoxingxing.playground.features.rocketmq.config.OrderMqProperties;
import com.xt.xiaoxingxing.shared.web.exception.GlobalExceptionHandler;
import com.xt.xiaoxingxing.shared.feign.user.client.UserClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** playground 学习服务启动入口。 */
@SpringBootApplication(scanBasePackages = "com.xt.xiaoxingxing.playground")
@EnableDiscoveryClient
@EnableFeignClients(clients = UserClient.class)
@EnableScheduling
@EnableConfigurationProperties(OrderMqProperties.class)
@Import(GlobalExceptionHandler.class)
public class PlaygroundApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaygroundApplication.class, args);
    }
}
