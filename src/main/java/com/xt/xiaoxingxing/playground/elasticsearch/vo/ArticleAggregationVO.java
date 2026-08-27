package com.xt.xiaoxingxing.playground.elasticsearch.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArticleAggregationVO {

    private long total;
    private List<TermBucketVO> categories;
    private List<TermBucketVO> tags;
    private List<TermBucketVO> difficulties;
    private List<MonthBucketVO> publishedByMonth;
    private ViewCountStatsVO viewCountStats;

    @Data
    @AllArgsConstructor
    public static class TermBucketVO {
        private String key;
        private long count;
    }

    @Data
    @AllArgsConstructor
    public static class MonthBucketVO {
        private String month;
        private long count;
    }

    @Data
    @AllArgsConstructor
    public static class ViewCountStatsVO {
        private long count;
        private Double min;
        private Double max;
        private Double avg;
        private double sum;
    }
}
