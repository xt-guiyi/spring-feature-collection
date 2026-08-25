package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 工作结果数据访问。 */
@Mapper
public interface XxlLearningWorkResultMapper {
    int insertIfAbsent(XxlLearningWorkResult result);

    List<XxlLearningWorkResult> selectPage(@Param("batchKey") String batchKey,
                                            @Param("offset") long offset,
                                            @Param("pageSize") int pageSize);

    long countPage(@Param("batchKey") String batchKey);
}
