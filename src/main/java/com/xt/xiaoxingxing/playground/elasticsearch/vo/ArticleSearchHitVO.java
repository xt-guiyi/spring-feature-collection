package com.xt.xiaoxingxing.playground.elasticsearch.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class ArticleSearchHitVO {

    private String id;
    private String title;
    private String summary;
    private String content;
    private String category;
    private List<String> tags;
    private String difficulty;

    @JsonFormat(pattern = "yyyy年MM月dd日 HH:mm", timezone = "Asia/Shanghai")
    private Instant publishedAt;
    private Long viewCount;
    private Boolean enabled;
    private Double score;
    private Map<String, List<String>> highlights;
}
