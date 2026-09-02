package com.xt.xiaoxingxing.playground.features.xxljob.mapper;

import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlWorkResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 工作结果数据访问。 */
@Mapper
public interface XxlWorkResultMapper {
    int insertIfAbsent(XxlWorkResult result);

    List<XxlWorkResult> selectPage(@Param("batchKey") String batchKey,
                                            @Param("offset") long offset,
                                            @Param("pageSize") int pageSize);

    long countPage(@Param("batchKey") String batchKey);
}
