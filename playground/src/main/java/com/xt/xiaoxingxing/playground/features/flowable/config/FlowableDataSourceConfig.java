package com.xt.xiaoxingxing.playground.features.flowable.config;

import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 将 Flowable 的 BPMN、DMN 引擎绑定到 playground PostgreSQL。
 *
 * <p>项目同时存在 business 与 playground 两个数据源，Spring Boot 默认会把
 * {@code @Primary} 的 businessDataSource 注入 Flowable 自动配置。这里通过
 * Flowable 官方 {@link EngineConfigurationConfigurer} 扩展点在引擎初始化前覆盖
 * 数据源和事务管理器，保证 ACT_* 表与学习模块业务表位于 demo 数据库。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FlowableDataSourceConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableProcessDataSourceConfigurer(
            @Qualifier("playgroundDataSource") DataSource dataSource,
            @Qualifier("playgroundTransactionManager") PlatformTransactionManager transactionManager) {
        return configuration -> {
            configuration.setDataSource(dataSource);
            configuration.setTransactionManager(transactionManager);
        };
    }

    @Bean
    public EngineConfigurationConfigurer<SpringDmnEngineConfiguration> flowableDmnDataSourceConfigurer(
            @Qualifier("playgroundDataSource") DataSource dataSource,
            @Qualifier("playgroundTransactionManager") PlatformTransactionManager transactionManager) {
        return configuration -> {
            configuration.setDataSource(dataSource);
            configuration.setTransactionManager(transactionManager);
        };
    }
}
