package com.xt.xiaoxingxing.playground.features.xxljob.mapper;

import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 工作批次数据访问。 */
@Mapper
public interface XxlBatchMapper {
    int insertIfAbsent(XxlBatch batch);

    XxlBatch selectByBatchKey(@Param("batchKey") String batchKey);

    /** 加锁查询批次。 */
    XxlBatch selectByIdForUpdate(@Param("id") long id);

    /** 刷新并返回批次状态。 */
    XxlBatch refreshStatusReturning(@Param("batchId") long batchId);

    List<Long> selectActiveIds();

    List<XxlBatch> selectPage(@Param("status") String status,
                                      @Param("offset") long offset,
                                      @Param("pageSize") int pageSize);

    long countPage(@Param("status") String status);
}
