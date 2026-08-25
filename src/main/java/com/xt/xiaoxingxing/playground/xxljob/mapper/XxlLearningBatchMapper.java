package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 工作批次数据访问。 */
@Mapper
public interface XxlLearningBatchMapper {
    int insertIfAbsent(XxlLearningBatch batch);

    XxlLearningBatch selectByBatchKey(@Param("batchKey") String batchKey);

    /** 加锁查询批次。 */
    XxlLearningBatch selectByIdForUpdate(@Param("id") long id);

    /** 刷新并返回批次状态。 */
    XxlLearningBatch refreshStatusReturning(@Param("batchId") long batchId);

    List<Long> selectActiveIds();

    List<XxlLearningBatch> selectPage(@Param("status") String status,
                                      @Param("offset") long offset,
                                      @Param("pageSize") int pageSize);

    long countPage(@Param("status") String status);
}
