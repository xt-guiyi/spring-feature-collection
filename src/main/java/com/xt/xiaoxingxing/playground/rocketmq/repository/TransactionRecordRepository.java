package com.xt.xiaoxingxing.playground.rocketmq.repository;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqTransactionRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 事务消息记录仓库。 */
@Repository("transactionRecordRepository")
@RequiredArgsConstructor
public class TransactionRecordRepository {

    private final MqTransactionRecordMapper transactionRecordMapper;

    /** 新增待处理的事务记录。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void prepare(String transactionId) {
        MqTransactionRecord record = new MqTransactionRecord();
        record.setTransactionId(transactionId);
        record.setCreatedAt(LocalDateTime.now());
        transactionRecordMapper.insertPrepared(record);
    }

    /** 将事务记录标记为已提交。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.MANDATORY)
    public boolean markCommitted(String transactionId) {
        return transactionRecordMapper.markCommitted(transactionId) == 1;
    }

    /** 将事务记录标记为已回滚。 */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean markRolledBack(String transactionId, String error) {
        String lastError = error == null || error.isBlank() ? "本地事务明确失败" : error;
        lastError = lastError.substring(0, Math.min(lastError.length(), 1000));
        return transactionRecordMapper.markRolledBack(transactionId, lastError) == 1;
    }

    /** 根据事务 ID 查询事务记录。 */
    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public MqTransactionRecord findById(String transactionId) {
        return transactionRecordMapper.selectById(transactionId);
    }

    /** 查询过期的待处理事务记录。 */
    @Transactional(transactionManager = "playgroundTransactionManager",
            propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<MqTransactionRecord> findExpiredPrepared(LocalDateTime expiredBefore, int batchSize) {
        return transactionRecordMapper.selectExpiredPreparedCandidates(expiredBefore, batchSize);
    }

}
