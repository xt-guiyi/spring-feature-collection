package com.xt.xiaoxingxing.playground.elasticsearch.config;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchConfig {

    /** 创建适配 Elasticsearch 日期格式的 JSON 映射器。 */
    @Bean
    public JsonpMapper elasticsearchJsonpMapper() {
        // Elasticsearch 的 numeric date 按 epoch_millis 解释，不能沿用 Jackson 3 默认的 epoch seconds。
        JsonMapper mapper = JsonMapper.builder()
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new Jackson3JsonpMapper(mapper);
    }
}
