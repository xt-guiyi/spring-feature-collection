package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 事务消息记录数据访问接口。 */
@Mapper
public interface MqTransactionRecordMapper {

    /** 新增待处理事务记录。 */
    int insertPrepared(MqTransactionRecord record);

    /** 将事务记录标记为已提交。 */
    int markCommitted(@Param("transactionId") String transactionId);

    /** 将事务记录标记为已回滚。 */
    int markRolledBack(@Param("transactionId") String transactionId,
                       @Param("lastError") String lastError);

    /** 查询过期的待处理事务记录。 */
    List<MqTransactionRecord> selectExpiredPreparedCandidates(
            @Param("expiredBefore") LocalDateTime expiredBefore,
            @Param("batchSize") int batchSize);

    /** 根据事务 ID 查询事务记录。 */
    MqTransactionRecord selectById(@Param("transactionId") String transactionId);

}
