package com.xt.xiaoxingxing.shared.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(
        basePackages = {
                "com.xt.xiaoxingxing.playground.postgresql.mapper",
                "com.xt.xiaoxingxing.playground.rabbitmq.mapper"
        },
        sqlSessionFactoryRef = "playgroundSqlSessionFactory"
)
public class PlaygroundMyBatisConfig {

    @Bean(name = "playgroundSqlSessionFactory")
    public SqlSessionFactory playgroundSqlSessionFactory(@Qualifier("playgroundDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 注意这里创建的是 MyBatis-Plus 提供的 MybatisConfiguration，而不是 MyBatis 原生
        // Configuration。它会把 CompositeEnumTypeHandler 注册成默认枚举处理器，因此：
        // 1. 普通 MyBatis XML 查询；
        // 2. MyBatis-Plus BaseMapper 查询；
        // 只要共用这个 SqlSessionFactory，就都能识别枚举字段上的 @EnumValue。
        MybatisConfiguration configuration = new MybatisConfiguration();
        // 普通 MyBatis XML 使用 user_id、order_no 等数据库列名，Plus 实体使用
        // userId、orderNo 等 Java 属性名；显式开启转换，避免依赖自动配置的隐式默认值。
        configuration.setMapUnderscoreToCamelCase(true);
        // 当前 SqlSessionFactory 是手动创建的，application.yaml 中的 MyBatis-Plus 自动配置
        // 不一定会应用到这里，因此显式使用 StdOutImpl 将 SQL、参数和影响行数打印到控制台。
        // 该方式适合学习和本地调试；生产环境应改用受日志级别控制的实现，并注意敏感参数脱敏。
        configuration.setLogImpl(StdOutImpl.class);
        bean.setConfiguration(configuration);
        // 自定义 SqlSessionFactory 后需要显式指定 XML 路径，否则拆分后的普通 MyBatis
        // Mapper XML 可能不会被 Spring Boot 的默认自动配置加载。
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml"));
        bean.setPlugins(mybatisPlusInterceptor());
        return bean.getObject();
    }

    @Bean(name = "playgroundTransactionManager")
    public DataSourceTransactionManager playgroundTransactionManager(@Qualifier("playgroundDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
