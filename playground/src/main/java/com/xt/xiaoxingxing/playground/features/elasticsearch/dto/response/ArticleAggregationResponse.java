package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArticleAggregationResponse {

    private long total;
    private List<TermBucketResponse> categories;
    private List<TermBucketResponse> tags;
    private List<TermBucketResponse> difficulties;
    private List<MonthBucketResponse> publishedByMonth;
    private ViewCountStatsResponse viewCountStats;

    @Data
    @AllArgsConstructor
    public static class TermBucketResponse {
        private String key;
        private long count;
    }

    @Data
    @AllArgsConstructor
    public static class MonthBucketResponse {
        private String month;
        private long count;
    }

    @Data
    @AllArgsConstructor
    public static class ViewCountStatsResponse {
        private long count;
        private Double min;
        private Double max;
        private Double avg;
        private double sum;
    }
}
