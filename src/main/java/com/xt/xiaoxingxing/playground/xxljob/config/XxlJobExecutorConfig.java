package com.xt.xiaoxingxing.playground.xxljob.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建并配置唯一的 XXL-JOB Spring Executor。 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobExecutorConfig {

    /** 创建与应用同生命周期的 Executor。 */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobProperties.Admin admin = properties.getAdmin();
        XxlJobProperties.Executor executor = properties.getExecutor();

        XxlJobSpringExecutor springExecutor = new XxlJobSpringExecutor();
        springExecutor.setAdminAddresses(admin.getAddresses());
        springExecutor.setAccessToken(admin.getAccessToken());
        springExecutor.setTimeout(admin.getTimeout());
        springExecutor.setAppname(executor.getAppname());
        springExecutor.setAddress(executor.getAddress());
        springExecutor.setIp(executor.getIp());
        springExecutor.setPort(executor.getPort());
        springExecutor.setLogPath(executor.getLogPath());
        springExecutor.setLogRetentionDays(executor.getLogRetentionDays());
        springExecutor.setExcludedPackage(executor.getExcludedPackage());
        return springExecutor;
    }
}
