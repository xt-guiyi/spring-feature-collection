package com.xt.xiaoxingxing.playground.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.validation.annotation.Validated;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 统一创建项目中的 MyBatis 数据源工厂。 */
@Configuration
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.mybatis")
@MapperScan(
        basePackages = {
                "com.xt.xiaoxingxing.playground.features.postgresql.mapper",
                "com.xt.xiaoxingxing.playground.features.rocketmq.mapper",
                "com.xt.xiaoxingxing.playground.features.xxljob.mapper",
                "com.xt.xiaoxingxing.playground.features.flowable.mapper"
        },
        sqlSessionFactoryRef = "playgroundSqlSessionFactory"
)
public class MyBatisConfig {

    /** Mapper XML 按数据源分组，避免跨库加载。 */
    @NotEmpty
    private Map<@NotBlank String, List<@NotBlank String>> mapperLocations;

    @NotNull
    private Boolean mapUnderscoreToCamelCase;

    @NotNull
    private Class<? extends Log> logImpl;

    @NotNull
    private DbType paginationDbType;

    @Bean(name = "businessSqlSessionFactory")
    @Primary
    public SqlSessionFactory businessSqlSessionFactory(
            @Qualifier("businessDataSource") DataSource dataSource) throws Exception {
        return createSqlSessionFactory(dataSource, "business");
    }

    @Bean(name = "playgroundSqlSessionFactory")
    public SqlSessionFactory playgroundSqlSessionFactory(
            @Qualifier("playgroundDataSource") DataSource dataSource) throws Exception {
        return createSqlSessionFactory(dataSource, "playground");
    }

    @Bean(name = "businessTransactionManager")
    @Primary
    public DataSourceTransactionManager businessTransactionManager(
            @Qualifier("businessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "playgroundTransactionManager")
    public DataSourceTransactionManager playgroundTransactionManager(
            @Qualifier("playgroundDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private SqlSessionFactory createSqlSessionFactory(
            DataSource dataSource, String mapperGroup) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(mapUnderscoreToCamelCase);
        configuration.setLogImpl(logImpl);
        bean.setConfiguration(configuration);

        List<Resource> resources = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : mapperLocations.getOrDefault(mapperGroup, List.of())) {
            resources.addAll(List.of(resolver.getResources(pattern)));
        }
        if (!resources.isEmpty()) {
            bean.setMapperLocations(resources.toArray(Resource[]::new));
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(paginationDbType));
        bean.setPlugins(interceptor);
        return bean.getObject();
    }
}
