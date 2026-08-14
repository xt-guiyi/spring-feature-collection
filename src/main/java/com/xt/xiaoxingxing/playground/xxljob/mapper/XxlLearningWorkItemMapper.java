package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningWorkItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 固定逻辑桶工作项的批量生成、分片领取与租约条件推进。 */
@Mapper
public interface XxlLearningWorkItemMapper {
    int insertGeneratedItems(@Param("batchId") long batchId, @Param("itemCount") int itemCount,
                             @Param("failEvery") int failEvery, @Param("failTimes") int failTimes);

    long countByBatchId(@Param("batchId") long batchId);

    List<XxlLearningWorkItem> claimShardItems(@Param("batchKey") String batchKey,
                                              @Param("batchSize") int batchSize,
                                              @Param("leaseTokenPrefix") String leaseTokenPrefix,
                                              @Param("leaseSeconds") int leaseSeconds,
                                              @Param("jobId") long jobId,
                                              @Param("logId") long logId,
                                              @Param("maxAttempts") int maxAttempts,
                                              @Param("shardIndex") int shardIndex,
                                              @Param("shardTotal") int shardTotal);

    int closeExpiredExhausted(@Param("batchKey") String batchKey,
                              @Param("maxAttempts") int maxAttempts,
                              @Param("shardIndex") int shardIndex,
                              @Param("shardTotal") int shardTotal,
                              @Param("lastError") String lastError);

    /** 防止处理策略被调小后，已耗尽次数的 RETRY_WAIT 永久失去领取资格。 */
    int closeRetryWaitExhausted(@Param("batchKey") String batchKey,
                                @Param("maxAttempts") int maxAttempts,
                                @Param("shardIndex") int shardIndex,
                                @Param("shardTotal") int shardTotal,
                                @Param("lastError") String lastError);

    int markRetryWait(@Param("id") long id, @Param("leaseToken") String leaseToken,
                      @Param("retryDelaySeconds") int retryDelaySeconds,
                      @Param("lastError") String lastError);

    int markDead(@Param("id") long id, @Param("leaseToken") String leaseToken,
                 @Param("lastError") String lastError);

    int markSuccess(@Param("id") long id, @Param("leaseToken") String leaseToken);

    List<XxlLearningWorkItem> selectPageByBatchKey(@Param("batchKey") String batchKey,
                                                    @Param("status") String status,
                                                    @Param("offset") long offset,
                                                    @Param("pageSize") int pageSize);

    long countPageByBatchKey(@Param("batchKey") String batchKey, @Param("status") String status);
}
