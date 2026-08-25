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
import java.util.Map;

/** 全项目 SqlSessionFactory 共用的 MyBatis 配置。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.mybatis")
public class ApplicationMyBatisProperties {

    /** Mapper XML 按数据源分组，避免跨库加载。 */
    @NotEmpty
    private Map<@NotBlank String, List<@NotBlank String>> mapperLocations;

    @NotNull
    private Boolean mapUnderscoreToCamelCase;

    @NotNull
    private Class<? extends Log> logImpl;

    @NotNull
    private DbType paginationDbType;
}
