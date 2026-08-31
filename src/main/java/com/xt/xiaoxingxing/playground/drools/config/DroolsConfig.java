package com.xt.xiaoxingxing.playground.drools.config;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Drools 规则容器配置。 */
@Configuration
public class DroolsConfig {

    /**
     * 从 classpath 加载 {@code META-INF/kmodule.xml} 和规则文件。
     *
     * <p>KieContainer 负责持有编译后的规则库，具体请求使用的 KieSession 由业务服务按请求创建。</p>
     */
    @Bean
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();
        return kieServices.getKieClasspathContainer();
    }
}
