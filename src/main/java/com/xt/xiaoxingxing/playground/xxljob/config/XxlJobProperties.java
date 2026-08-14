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

/**
 * XXL-JOB Executor 的类型安全配置。
 *
 * <p>官方 {@code xxl-job-core} 不是 Spring Boot Starter，不会自动读取这些属性并创建 Executor。
 * 因此本学习案例先把 YAML 绑定到本类，再由 {@link XxlJobExecutorConfig} 显式调用官方 setter。
 * 这种写法也让“Admin 连接参数”和“Executor 对外注册地址”两个方向的网络配置更容易区分。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /**
     * 虽然保留嵌套对象实例以便 Spring Binder 绑定，但不为任何运行参数提供 Java 默认值。
     *
     * <p>这样地址、端口、密钥等部署差异只能在 YAML 或环境变量中声明；漏配时由
     * {@link Validated} 和 Jakarta Validation 在应用启动阶段明确失败，而不会悄悄连到某个本地默认地址。</p>
     */
    @Valid
    private Admin admin = new Admin();

    @Valid
    private Executor executor = new Executor();

    /** 与具体业务日期计算有关、随部署地区变化的运行配置。 */
    @Valid
    private Learning learning = new Learning();

    /** Executor 主动访问 Admin 时使用的配置。 */
    @Data
    public static class Admin {

        /**
         * Admin 根地址，多个节点可用逗号分隔。
         *
         * <p>3.4.2 客户端会自行追加 {@code /api}，所以这里不要再保留旧版
         * {@code /xxl-job-admin} context-path 后缀。</p>
         */
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

    /** Admin 反向触发 Executor 以及 Executor 本地日志所需的配置。 */
    @Data
    public static class Executor {

        /** 显式配置为 {@code false} 时不创建 Executor Bean，避免官方 disabled 停机分支的空对象风险。 */
        @NotNull(message = "xxl.job.executor.enabled 必须显式声明 true 或 false")
        private Boolean enabled;

        /** Executor 集群标识，必须与 Admin 中配置的 AppName 完全一致。 */
        @NotBlank(message = "xxl.job.executor.appname 不能为空")
        private String appname;

        /**
         * 注册给 Admin 的完整地址；Docker 中的 Admin 需要通过该地址回连业务进程。
         * 空值时官方客户端才会根据 ip 和 port 自动生成地址。
         */
        @NotNull(message = "xxl.job.executor.address 必须显式声明；不使用时请配置为空字符串")
        private String address;

        /** address 为空时参与自动注册地址生成；多网卡环境应显式配置可达地址。 */
        @NotNull(message = "xxl.job.executor.ip 必须显式声明；不使用时请配置为空字符串")
        private String ip;

        /** XXL-JOB 内嵌 Netty 服务端口，不是 Spring MVC 的 server.port。 */
        @Min(value = 1, message = "xxl.job.executor.port 必须是有效端口")
        @Max(value = 65535, message = "xxl.job.executor.port 不能大于 65535")
        private int port;

        /** Rolling Log 文件目录；容器环境应挂载可写且持久化的卷。 */
        @NotBlank(message = "xxl.job.executor.log-path 不能为空")
        private String logPath;

        /** Executor 文件日志保留天数；官方清理线程要求至少为 3 天。 */
        @Min(value = 3, message = "xxl.job.executor.log-retention-days 不能小于 3 天")
        private int logRetentionDays;

        /** 扫描 {@code @XxlJob} 时需要排除的包前缀，多个值用逗号分隔。 */
        @NotNull(message = "xxl.job.executor.excluded-package 必须显式声明；不排除时请配置为空字符串")
        private String excludedPackage;
    }

    /**
     * 业务日期使用的时区。
     *
     * <p>它不是 JVM 的 {@code user.timezone}：同一应用即使部署在 UTC 容器中，也可以按业务所在地计算
     * “昨天”。使用 {@link ZoneId} 而不是字符串可让 Spring 在绑定阶段校验时区名称是否合法。</p>
     */
    @Data
    public static class Learning {

        @NotNull(message = "xxl.job.learning.business-zone 不能为空")
        private ZoneId businessZone;
    }
}
