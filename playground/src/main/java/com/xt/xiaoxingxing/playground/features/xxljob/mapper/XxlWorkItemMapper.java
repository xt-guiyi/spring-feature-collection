package com.xt.xiaoxingxing.playground.features.xxljob.mapper;

import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlWorkItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 工作项数据访问。 */
@Mapper
public interface XxlWorkItemMapper {
    int insertGeneratedItems(@Param("batchId") long batchId, @Param("itemCount") int itemCount,
                             @Param("failEvery") int failEvery, @Param("failTimes") int failTimes);

    long countByBatchId(@Param("batchId") long batchId);

    List<XxlWorkItem> claimShardItems(@Param("batchSize") int batchSize,
                                              @Param("leaseTokenPrefix") String leaseTokenPrefix,
                                              @Param("leaseSeconds") int leaseSeconds,
                                              @Param("jobId") long jobId,
                                              @Param("logId") long logId,
                                              @Param("maxAttempts") int maxAttempts,
                                              @Param("shardIndex") int shardIndex,
                                              @Param("shardTotal") int shardTotal);

    List<Long> closeExpiredExhausted(@Param("maxAttempts") int maxAttempts,
                                    @Param("shardIndex") int shardIndex,
                                    @Param("shardTotal") int shardTotal,
                                    @Param("lastError") String lastError);

    /** 关闭已耗尽重试次数的等待项。 */
    List<Long> closeRetryWaitExhausted(@Param("maxAttempts") int maxAttempts,
                                      @Param("shardIndex") int shardIndex,
                                      @Param("shardTotal") int shardTotal,
                                      @Param("lastError") String lastError);

    int renewLease(@Param("id") long id, @Param("leaseToken") String leaseToken,
                   @Param("leaseSeconds") int leaseSeconds);

    int markRetryWait(@Param("id") long id, @Param("leaseToken") String leaseToken,
                      @Param("retryDelaySeconds") int retryDelaySeconds,
                      @Param("lastError") String lastError);

    int markDead(@Param("id") long id, @Param("leaseToken") String leaseToken,
                 @Param("lastError") String lastError);

    int markSuccess(@Param("id") long id, @Param("leaseToken") String leaseToken);

    List<XxlWorkItem> selectPageByBatchKey(@Param("batchKey") String batchKey,
                                                    @Param("status") String status,
                                                    @Param("offset") long offset,
                                                    @Param("pageSize") int pageSize);

    long countPageByBatchKey(@Param("batchKey") String batchKey, @Param("status") String status);
}
