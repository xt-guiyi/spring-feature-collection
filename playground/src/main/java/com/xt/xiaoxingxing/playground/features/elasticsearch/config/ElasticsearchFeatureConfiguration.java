package com.xt.xiaoxingxing.playground.features.elasticsearch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Elasticsearch 文章学习模块的业务参数配置。 */
@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchFeatureConfiguration {
}
