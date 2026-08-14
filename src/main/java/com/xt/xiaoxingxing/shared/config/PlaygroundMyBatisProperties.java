package com.xt.xiaoxingxing.shared.config;

import com.baomidou.mybatisplus.annotation.DbType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.logging.Log;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 手动创建 Playground SqlSessionFactory 时需要的运行参数。
 *
 * <p>这是自定义工厂的补充配置。若依赖 MyBatis-Plus 自动配置，标准 mybatis-plus 前缀会
 * 自动生效；本项目有独立数据源和工厂，因此必须在这里显式绑定，避免 Java 写死后覆盖 YAML。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "playground.mybatis")
public class PlaygroundMyBatisProperties {

    @NotEmpty
    private List<@NotBlank String> mapperLocations;

    @NotNull
    private Boolean mapUnderscoreToCamelCase;

    @NotNull
    private Class<? extends Log> logImpl;

    @NotNull
    private DbType paginationDbType;
}
