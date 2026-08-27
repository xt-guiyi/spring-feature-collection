package com.xt.xiaoxingxing.playground.elasticsearch.vo;

import lombok.Data;

/** 固定文章批量写入结果。 */
@Data
public class ArticleBulkResultVO {

    private int total;
    private int succeeded;
    private int failed;
}
