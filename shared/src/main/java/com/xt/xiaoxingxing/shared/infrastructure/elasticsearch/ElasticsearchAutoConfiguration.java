package com.xt.xiaoxingxing.shared.infrastructure.elasticsearch;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** 项目统一的 Elasticsearch JSON 日期映射配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "shared.infrastructure.elasticsearch",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnClass({JsonpMapper.class, Jackson3JsonpMapper.class})
public class ElasticsearchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JsonpMapper.class)
    public JsonpMapper elasticsearchJsonpMapper() {
        JsonMapper mapper = JsonMapper.builder()
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new Jackson3JsonpMapper(mapper);
    }
}
