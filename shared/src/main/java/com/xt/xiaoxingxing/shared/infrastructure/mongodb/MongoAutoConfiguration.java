package com.xt.xiaoxingxing.shared.infrastructure.mongodb;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** 项目统一的 MongoDB 审计配置。连接参数仍由各服务自行决定。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "shared.infrastructure.mongodb",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnClass(EnableMongoAuditing.class)
@EnableMongoAuditing
public class MongoAutoConfiguration {
}
