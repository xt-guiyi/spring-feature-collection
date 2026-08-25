package com.xt.xiaoxingxing.shared.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/** 统一创建项目中的 MyBatis 数据源工厂。 */
@Configuration
@EnableConfigurationProperties(ApplicationMyBatisProperties.class)
@MapperScan(
        basePackages = {
                "com.xt.xiaoxingxing.playground.postgresql.mapper",
                "com.xt.xiaoxingxing.playground.rocketmq.mapper",
                "com.xt.xiaoxingxing.playground.xxljob.mapper"
        },
        sqlSessionFactoryRef = "playgroundSqlSessionFactory"
)
public class MyBatisConfig {

    @Bean(name = "businessSqlSessionFactory")
    @Primary
    public SqlSessionFactory businessSqlSessionFactory(
            @Qualifier("businessDataSource") DataSource dataSource,
            ApplicationMyBatisProperties properties) throws Exception {
        return createSqlSessionFactory(dataSource, properties, "business");
    }

    @Bean(name = "playgroundSqlSessionFactory")
    public SqlSessionFactory playgroundSqlSessionFactory(
            @Qualifier("playgroundDataSource") DataSource dataSource,
            ApplicationMyBatisProperties properties) throws Exception {
        return createSqlSessionFactory(dataSource, properties, "playground");
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
            DataSource dataSource, ApplicationMyBatisProperties properties, String mapperGroup) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(properties.getMapUnderscoreToCamelCase());
        configuration.setLogImpl(properties.getLogImpl());
        bean.setConfiguration(configuration);

        List<Resource> resources = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : properties.getMapperLocations().getOrDefault(mapperGroup, List.of())) {
            resources.addAll(List.of(resolver.getResources(pattern)));
        }
        if (!resources.isEmpty()) {
            bean.setMapperLocations(resources.toArray(Resource[]::new));
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(properties.getPaginationDbType()));
        bean.setPlugins(interceptor);
        return bean.getObject();
    }
}
