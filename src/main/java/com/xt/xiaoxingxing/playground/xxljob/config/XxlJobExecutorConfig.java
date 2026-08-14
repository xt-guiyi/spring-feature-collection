package com.xt.xiaoxingxing.playground.xxljob.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 官方 Spring Executor 的显式装配。
 *
 * <p>这里有两个刻意的边界：</p>
 * <ol>
 *     <li>应用只创建一个 {@link XxlJobSpringExecutor}，避免官方实现中的静态上下文和单例引用互相覆盖；</li>
 *     <li>关闭功能时完全不创建 Bean，而不是创建后调用 {@code setEnabled(false)}。这样既不开放内嵌端口，
 *     也不会让未启动完整的 Executor 进入销毁生命周期。</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobExecutorConfig {

    /**
     * 当且仅当显式启用时创建 Executor。
     *
     * <p>{@code XxlJobSpringExecutor} 已通过 Spring 的
     * {@code SmartInitializingSingleton}/{@code DisposableBean} 管理启动和销毁，不能再配置
     * {@code initMethod="start"}/{@code destroyMethod="destroy"}，否则会重复管理同一组线程和端口。</p>
     */
    @Bean
    @ConditionalOnProperty(name = "xxl.job.executor.enabled", havingValue = "true")
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
