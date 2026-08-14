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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(PlaygroundMyBatisProperties.class)
@MapperScan(
        basePackages = {
                "com.xt.xiaoxingxing.playground.postgresql.mapper",
                "com.xt.xiaoxingxing.playground.rocketmq.mapper",
                "com.xt.xiaoxingxing.playground.xxljob.mapper"
        },
        sqlSessionFactoryRef = "playgroundSqlSessionFactory"
)
public class PlaygroundMyBatisConfig {

    @Bean(name = "playgroundSqlSessionFactory")
    public SqlSessionFactory playgroundSqlSessionFactory(
            @Qualifier("playgroundDataSource") DataSource dataSource,
            PlaygroundMyBatisProperties properties) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 注意这里创建的是 MyBatis-Plus 提供的 MybatisConfiguration，而不是 MyBatis 原生
        // Configuration。它会把 CompositeEnumTypeHandler 注册成默认枚举处理器，因此：
        // 1. 普通 MyBatis XML 查询；
        // 2. MyBatis-Plus BaseMapper 查询；
        // 只要共用这个 SqlSessionFactory，就都能识别枚举字段上的 @EnumValue。
        MybatisConfiguration configuration = new MybatisConfiguration();
        // 普通 MyBatis XML 使用 user_id、order_no 等数据库列名，Plus 实体使用
        // userId、orderNo 等 Java 属性名；开关由 playground.mybatis.map-underscore-to-camel-case
        // 控制，避免手动工厂绕开 YAML 后悄悄采用另一套映射规则。
        configuration.setMapUnderscoreToCamelCase(properties.getMapUnderscoreToCamelCase());
        // 当前 SqlSessionFactory 是手动创建的，MyBatis-Plus 自动配置不一定会应用到这里。
        // 因此日志实现也由 playground.mybatis.log-impl 传入，避免 Java 配置重新覆盖 YAML。
        configuration.setLogImpl(properties.getLogImpl());
        bean.setConfiguration(configuration);
        // 自定义 SqlSessionFactory 后需要显式指定 XML 路径，否则拆分后的普通 MyBatis
        // Mapper XML 可能不会被 Spring Boot 的默认自动配置加载。支持配置多个 Ant 风格路径。
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> mapperLocations = new ArrayList<>();
        for (String locationPattern : properties.getMapperLocations()) {
            mapperLocations.addAll(List.of(resolver.getResources(locationPattern)));
        }
        bean.setMapperLocations(mapperLocations.toArray(Resource[]::new));
        bean.setPlugins(mybatisPlusInterceptor(properties));
        return bean.getObject();
    }

    @Bean(name = "playgroundTransactionManager")
    public DataSourceTransactionManager playgroundTransactionManager(@Qualifier("playgroundDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PlaygroundMyBatisProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页方言不同会生成不同 LIMIT/OFFSET 语法，因此它属于运行数据库配置，不能写死在 Java 中。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(properties.getPaginationDbType()));
        return interceptor;
    }
}
