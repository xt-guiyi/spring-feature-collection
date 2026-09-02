package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArticleCursorPageResponse {

    private List<ArticleSearchHitResponse> list;
    private ArticleCursorTokenResponse cursor;
    private boolean hasMore;
}
