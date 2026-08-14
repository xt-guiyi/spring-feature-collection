package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 批次幂等创建、集合状态刷新与运维查询。 */
@Mapper
public interface XxlLearningBatchMapper {
    int insertIfAbsent(XxlLearningBatch batch);

    XxlLearningBatch selectByBatchKey(@Param("batchKey") String batchKey);

    XxlLearningBatch selectById(@Param("id") long id);

    /** 在刷新集合状态前锁住批次行，让多个分片按顺序重新读取并计算最新工作项快照。 */
    XxlLearningBatch selectByIdForUpdate(@Param("id") long id);

    int refreshStatus(@Param("batchId") long batchId);

    /** 刷新后返回数据库基于全部工作项推导出的最新批次状态。 */
    XxlLearningBatch refreshStatusReturning(@Param("batchId") long batchId);

    List<XxlLearningBatch> selectPage(@Param("status") String status,
                                      @Param("offset") long offset,
                                      @Param("pageSize") int pageSize);

    long countPage(@Param("status") String status);
}
