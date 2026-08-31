package com.xt.xiaoxingxing.shared.config;

import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * 先把 url、username、password、driver-class-name 绑定到 Spring Boot 的通用数据源属性对象。
     * 不能把这些配置直接绑定到 HikariDataSource：通用属性叫 url，而 Hikari 对应属性叫 jdbcUrl，
     * 直接绑定会导致驱动类存在但连接地址为空，并抛出“jdbcUrl is required with driverClassName”。
     */
    @Bean(name = "businessDataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.business")
    public DataSourceProperties businessDataSourceProperties() {
        return new DataSourceProperties();
    }

    /** Playground PostgreSQL 学习库使用独立的配置对象，避免与业务数据源串用连接信息。 */
    @Bean(name = "playgroundDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.playground")
    public DataSourceProperties playgroundDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * initializeDataSourceBuilder 会选择当前类路径中的 Hikari，并把通用 url 转换为 Hikari jdbcUrl。
     */
    @Bean(name = "businessDataSource")
    @Primary
    public DataSource businessDataSource(
            @Qualifier("businessDataSourceProperties") DataSourceProperties businessDataSourceProperties) {
        return businessDataSourceProperties.initializeDataSourceBuilder().build();
    }

    /**
     * 使用 playground 前缀创建供学习模块共同使用的连接池。
     * {@link FlywayDataSource} 明确告诉 Spring Boot：Flyway 只能在 demo 库执行迁移，
     * 不要因为 businessDataSource 是 {@link Primary} 就误迁移主库。
     */
    @FlywayDataSource
    @Bean(name = "playgroundDataSource")
    public DataSource playgroundDataSource(
            @Qualifier("playgroundDataSourceProperties") DataSourceProperties playgroundDataSourceProperties) {
        return playgroundDataSourceProperties.initializeDataSourceBuilder().build();
    }
}
