package com.xt.xiaoxingxing.playground.elasticsearch.vo;

import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleCursorToken;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArticleCursorPageVO {

    private List<ArticleSearchHitVO> list;
    private ArticleCursorToken cursor;
    private boolean hasMore;
}
