package com.xt.xiaoxingxing.playground.mongo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB 学习模块配置。
 *
 * <p>{@link EnableMongoAuditing} 会让 Spring Data MongoDB 自动维护实体上的
 * {@code @CreatedDate} 和 {@code @LastModifiedDate} 字段。连接、数据库名和索引自动创建
 * 仍由 application.yaml 中的 Spring Boot 配置负责，不需要手工创建 MongoClient。</p>
 */
@Configuration
@EnableMongoAuditing
public class MongoLearningConfig {
}
