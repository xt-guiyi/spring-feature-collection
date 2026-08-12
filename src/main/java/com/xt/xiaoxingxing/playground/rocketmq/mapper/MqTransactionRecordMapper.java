package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RocketMQ 事务消息状态的持久化接口。
 *
 * <p>事务检查器只依赖本表的持久事实而不读取 JVM 内存；过期 PREPARED 需要通过条件更新抢占回滚终态。
 * 所有更新方法都用状态条件保护，0 行意味着事务状态已由其他路径终结，调用方必须重新读取而不能覆盖。</p>
 */
@Mapper
public interface MqTransactionRecordMapper {

    int insertPrepared(MqTransactionRecord record);

    int markCommitted(@Param("transactionId") String transactionId, @Param("orderId") Long orderId);

    int markRolledBack(@Param("transactionId") String transactionId, @Param("lastError") String lastError);

    List<MqTransactionRecord> selectExpiredPreparedCandidates(
            @Param("expiredBefore") LocalDateTime expiredBefore,
            @Param("batchSize") int batchSize);

    MqTransactionRecord selectById(@Param("transactionId") String transactionId);

    List<MqTransactionRecord> selectPage(@Param("status") String status,
                                         @Param("offset") long offset,
                                         @Param("pageSize") int pageSize);

    long countPage(@Param("status") String status);
}
