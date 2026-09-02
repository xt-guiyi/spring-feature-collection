package com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response;

import lombok.Data;

/** 固定文章批量写入结果。 */
@Data
public class ArticleBulkResultResponse {

    private int total;
    private int succeeded;
    private int failed;
}
