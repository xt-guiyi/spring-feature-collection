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

    /**
     * 插入 PREPARED 记录。
     *
     * <p>活跃记录由 {@code business_type + business_key + operation_type} 部分唯一索引防重。
     * Mapper 不做“先查再插”，因为该写法存在并发窗口。</p>
     */
    int insertPrepared(MqTransactionRecord record);

    int markCommitted(@Param("transactionId") String transactionId);

    int markRolledBack(@Param("transactionId") String transactionId, @Param("lastError") String lastError);

    /** 按通用业务三元组统计已提交记录；活跃部分唯一索引保证正常结果最多为 1。 */
    int countCommitted(@Param("businessType") String businessType,
                       @Param("businessKey") String businessKey,
                       @Param("operationType") String operationType);

    List<MqTransactionRecord> selectExpiredPreparedCandidates(
            @Param("expiredBefore") LocalDateTime expiredBefore,
            @Param("batchSize") int batchSize);

    /** transactionId 同时也是事务消息信封的 messageId，Broker 回查直接按本表主键读取。 */
    MqTransactionRecord selectById(@Param("transactionId") String transactionId);

}
