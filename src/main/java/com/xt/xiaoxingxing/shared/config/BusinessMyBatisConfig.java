package com.xt.xiaoxingxing.shared.config;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 业务数据源 MyBatis-Plus 配置。
 * <p>
 * 当前业务模块（user/order/product）尚未接入数据库，此配置为预留。
 * 后续业务模块需要 MyBatis mapper 时，可在此类上添加 @MapperScan 扫描对应包。
 */
@Configuration
public class BusinessMyBatisConfig {

    @Bean(name = "businessSqlSessionFactory")
    @Primary
    public SqlSessionFactory businessSqlSessionFactory(@Qualifier("businessDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        return bean.getObject();
    }

    @Bean(name = "businessTransactionManager")
    @Primary
    public DataSourceTransactionManager businessTransactionManager(@Qualifier("businessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
