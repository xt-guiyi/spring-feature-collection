package com.xt.xiaoxingxing.playground.rocketmq.infrastructure;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqTransactionRecordMapper;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RocketMQ 事务记录的持久化边界。
 *
 * <p>这个类虽然使用 MyBatis Mapper，但不向业务层暴露“插入一行、更新一行”这样的数据库细节，
 * 而是只表达 prepare、提交、回滚和查询终态等事务消息语义。Broker 回查器、孤儿清理任务以及后续
 * 业务服务都通过同一入口竞争 {@code PREPARED -> COMMITTED/ROLLED_BACK}，不能各自覆盖状态。</p>
 *
 * <p>事务边界必须区分两类写入：</p>
 * <ol>
 *     <li>PREPARED 与明确失败后的 ROLLED_BACK 使用 {@code REQUIRES_NEW}，需要独立留下回查事实；</li>
 *     <li>COMMITTED 必须加入业务事实所在的本地事务，业务数据和事务终态只能一起提交或回滚。</li>
 * </ol>
 */
@Slf4j
@Repository("transactionRecordRepository")
@RequiredArgsConstructor
public class TransactionRecordRepository {

    private final MqTransactionRecordMapper transactionRecordMapper;

    /**
     * 为一次通用业务操作独立写入 PREPARED。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>第1步：校验事务 ID 和业务三元组都不是空白字符串；transactionId 同时就是信封 messageId；</li>
     *     <li>第2步：构造只含通用协调字段的 PREPARED 记录，不在基础设施层解释 ORDER、CREATE 等枚举；</li>
     *     <li>第3步：直接插入，不使用“先查后插”；</li>
     *     <li>第4步：由业务三元组的活跃部分唯一索引裁决并发重复请求。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public MqTransactionRecord prepare(String transactionId,
                                       String businessType,
                                       String businessKey,
                                       String operationType) {
        requireTransactionId(transactionId);
        requireText(businessType, "businessType不能为空");
        requireText(businessKey, "businessKey不能为空");
        requireText(operationType, "operationType不能为空");
        MqTransactionRecord record = newPreparedRecord(
                transactionId, businessType, businessKey, operationType);
        return insertPrepared(record,
                "该业务操作已有进行中或已提交的事务记录，不能重复执行: businessType="
                        + businessType + ", businessKey=" + businessKey + ", operationType=" + operationType);
    }

    /**
     * 将 PREPARED 条件更新为 COMMITTED。
     *
     * <p>这里使用 {@code MANDATORY} 而不是 {@code REQUIRES_NEW}：调用方必须已经开启包含业务事实的
     * PostgreSQL 本地事务。若 Checker 或清理任务先抢到 ROLLED_BACK，本次更新影响 0 行并返回 false，
     * 调用方必须抛异常让本次业务写入一起回滚。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.MANDATORY)
    public boolean markCommitted(String transactionId) {
        requireTransactionId(transactionId);
        if (transactionRecordMapper.markCommitted(transactionId) == 1) {
            return true;
        }
        // 第2次读取只用于识别并发赢家，不会覆盖其终态。当前外层本地事务是否回滚由调用方决定。
        MqTransactionRecord latest = transactionRecordMapper.selectById(transactionId);
        log.warn("事务记录COMMITTED条件更新未命中: transactionId={}, latestStatus={}, "
                        + "businessType={}, businessKey={}, operationType={}",
                transactionId,
                latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getBusinessType(),
                latest == null ? null : latest.getBusinessKey(),
                latest == null ? null : latest.getOperationType());
        return false;
    }

    /**
     * 明确失败时用独立短事务抢占 ROLLED_BACK。
     *
     * <p>返回 true 表示本次把 PREPARED 改成了 ROLLED_BACK；返回 false 表示本地业务事务、Broker Checker、
     * 清理任务或另一实例已经先确定状态。此时只重读并记录当前赢家，绝不能执行无条件 UPDATE。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean markRolledBack(String transactionId, String error) {
        requireTransactionId(transactionId);
        String lastError = error == null || error.isBlank() ? "本地事务明确失败" : error;
        lastError = lastError.substring(0, Math.min(lastError.length(), 1000));
        if (transactionRecordMapper.markRolledBack(transactionId, lastError) == 1) {
            return true;
        }
        MqTransactionRecord latest = transactionRecordMapper.selectById(transactionId);
        log.debug("事务记录ROLLED_BACK条件更新未命中: transactionId={}, latestStatus={}",
                transactionId, latest == null ? null : latest.getStatus());
        return false;
    }

    /** 按信封 messageId（同时也是事务表主键）读取唯一持久记录。 */
    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public MqTransactionRecord findById(String transactionId) {
        requireTransactionId(transactionId);
        return transactionRecordMapper.selectById(transactionId);
    }

    /**
     * 短事务读取过期 PREPARED 候选。
     *
     * <p>候选对象只是一个可能已经过时的快照；调用方必须再调用 {@link #markRolledBack(String, String)}
     * 参与终态竞争，不能因为 SELECT 看见 PREPARED 就直接向 Broker 宣告回滚。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager",
            propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<MqTransactionRecord> findExpiredPrepared(LocalDateTime expiredBefore, int batchSize) {
        BusinessAssert.notNull(expiredBefore, "事务清理截止时间不能为空");
        BusinessAssert.isTrue(batchSize > 0 && batchSize <= 1000, "事务清理批量必须在1到1000之间");
        return transactionRecordMapper.selectExpiredPreparedCandidates(expiredBefore, batchSize);
    }

    /**
     * 按通用业务三元组判断某次业务操作是否已经提交。
     *
     * <p>数据库活跃部分唯一索引保证正常结果最多为 1，因此这里明确要求计数等于 1。
     * 业务层自行决定三元组取值，持久化层不会把它限制为订单、支付等特定协议。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public boolean isCommitted(String businessType, String businessKey, String operationType) {
        requireText(businessType, "businessType不能为空");
        requireText(businessKey, "businessKey不能为空");
        requireText(operationType, "operationType不能为空");
        return transactionRecordMapper.countCommitted(businessType, businessKey, operationType) == 1;
    }

    private MqTransactionRecord newPreparedRecord(String transactionId,
                                                  String businessType,
                                                  String businessKey,
                                                  String operationType) {
        MqTransactionRecord record = new MqTransactionRecord();
        record.setTransactionId(transactionId);
        record.setBusinessType(businessType);
        record.setBusinessKey(businessKey);
        record.setOperationType(operationType);
        record.setStatus("PREPARED");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        return record;
    }

    private MqTransactionRecord insertPrepared(MqTransactionRecord record, String conflictMessage) {
        try {
            BusinessAssert.isTrue(transactionRecordMapper.insertPrepared(record) == 1,
                    "事务PREPARED记录写入失败");
            return record;
        } catch (DuplicateKeyException duplicateKeyException) {
            // 主键及活跃业务部分唯一索引都在这里进行最终并发裁决；ROLLED_BACK 不占用重试资格。
            BusinessException businessException = new BusinessException(conflictMessage);
            businessException.addSuppressed(duplicateKeyException);
            throw businessException;
        }
    }

    private void requireTransactionId(String transactionId) {
        requireText(transactionId, "transactionId/messageId不能为空");
    }

    private void requireText(String value, String message) {
        BusinessAssert.isTrue(value != null && !value.isBlank(), message);
    }
}
