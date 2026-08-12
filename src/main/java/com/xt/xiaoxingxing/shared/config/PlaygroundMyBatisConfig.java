package com.xt.xiaoxingxing.shared.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
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
                "com.xt.xiaoxingxing.playground.rocketmq.mapper"
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
        // 不一定会应用到这里，因此这里也必须显式使用 SLF4J，避免 Java 配置重新覆盖 YAML。
        // SQL 日志默认不会刷屏；需要排查时再通过 logging.level 按 Mapper 包名开启 DEBUG。
        configuration.setLogImpl(Slf4jImpl.class);
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
