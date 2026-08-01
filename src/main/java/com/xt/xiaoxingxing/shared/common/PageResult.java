package com.xt.xiaoxingxing.shared.common;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {

    private List<T> list;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;

    public static <T> PageResult<T> empty() {
        PageResult<T> result = new PageResult<>();
        result.setList(Collections.emptyList());
        result.setTotal(0L);
        result.setPageNum(1);
        result.setPageSize(10);
        return result;
    }
}
