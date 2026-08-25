package com.xt.xiaoxingxing.playground.xxljob.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/** XXL-JOB 配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /** Admin 连接配置。 */
    @Valid
    private Admin admin = new Admin();

    @Valid
    private Executor executor = new Executor();

    /** 学习任务配置。 */
    @Valid
    private Learning learning = new Learning();

    /** Admin 连接参数。 */
    @Data
    public static class Admin {

        /** Admin 根地址，多个地址用逗号分隔；客户端自动追加 {@code /api}。 */
        @NotBlank(message = "xxl.job.admin.addresses 不能为空")
        private String addresses;

        /** 必须与 Admin 的 {@code xxl.job.accessToken} 保持一致。 */
        @NotBlank(message = "xxl.job.admin.access-token 不能为空")
        private String accessToken;

        /** Executor 请求 Admin 的超时时间，单位秒；官方实现只接受 1 到 10 秒。 */
        @Min(value = 1, message = "xxl.job.admin.timeout 不能小于 1 秒")
        @Max(value = 10, message = "xxl.job.admin.timeout 不能大于 10 秒")
        private int timeout;
    }

    /** Executor 注册与运行参数。 */
    @Data
    public static class Executor {

        /** Executor 集群标识，必须与 Admin 中配置的 AppName 完全一致。 */
        @NotBlank(message = "xxl.job.executor.appname 不能为空")
        private String appname;

        /** 注册给 Admin 的完整地址；为空时根据 ip 和 port 生成。 */
        @NotNull(message = "xxl.job.executor.address 必须显式声明；不使用时请配置为空字符串")
        private String address;

        /** 自动注册使用的 IP；多网卡环境应显式配置。 */
        @NotNull(message = "xxl.job.executor.ip 必须显式声明；不使用时请配置为空字符串")
        private String ip;

        /** XXL-JOB 内嵌 Netty 服务端口，不是 Spring MVC 的 server.port。 */
        @Min(value = 1, message = "xxl.job.executor.port 必须是有效端口")
        @Max(value = 65535, message = "xxl.job.executor.port 不能大于 65535")
        private int port;

        /** Rolling Log 目录。 */
        @NotBlank(message = "xxl.job.executor.log-path 不能为空")
        private String logPath;

        /** Executor 日志保留天数，最少 3 天。 */
        @Min(value = 3, message = "xxl.job.executor.log-retention-days 不能小于 3 天")
        private int logRetentionDays;

        /** 扫描 {@code @XxlJob} 时需要排除的包前缀，多个值用逗号分隔。 */
        @NotNull(message = "xxl.job.executor.excluded-package 必须显式声明；不排除时请配置为空字符串")
        private String excludedPackage;
    }

    /** 业务日期时区。 */
    @Data
    public static class Learning {

        @NotNull(message = "xxl.job.learning.business-zone 不能为空")
        private ZoneId businessZone;
    }
}
