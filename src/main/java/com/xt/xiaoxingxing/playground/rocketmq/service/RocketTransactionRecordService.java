package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqTransactionRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.TransactionOrderCommandPayload;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务消息 PREPARED/COMMITTED/ROLLED_BACK 持久记录的状态服务。
 *
 * <p>Broker checker 和主动清理器都不直接覆盖终态：它们共用同一个只允许
 * {@code PREPARED -> ROLLED_BACK} 的条件更新，更新 0 行后必须重读持久状态。</p>
 */
@Service
@RequiredArgsConstructor
public class RocketTransactionRecordService {

    private final MqTransactionRecordMapper transactionRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 在半消息发送前独立提交 PREPARED，使随后发生进程重启时 Broker 仍有可查询的持久依据。
     * 这条记录不声称订单已创建，只有本地订单事务更新为 COMMITTED 后才代表业务成功。
     *
     * <p>实现步骤：第1步序列化可持久命令；第2步用独立事务插入 PREPARED，
     * 活跃业务键部分唯一索引拒绝并发重复链路。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public MqTransactionRecord prepare(String transactionId,
                                       String businessKey,
                                       TransactionOrderCommandPayload command) {
        // 第1步：将完整本地事务命令序列化，回查和人工排障不能依赖 HTTP 对象或 JVM 内存 Map。
        String requestPayload;
        try {
            requestPayload = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("事务订单命令序列化失败", exception);
        }
        // 第2步：独立事务插入 PREPARED。部分唯一索引只让 PREPARED/COMMITTED 占用 businessKey：
        // 进行中或已成功的订单号不能并发重开，而已持久回滚的命令可以用新 transactionId 受控重试。
        MqTransactionRecord record = new MqTransactionRecord();
        record.setTransactionId(transactionId);
        record.setBusinessKey(businessKey);
        record.setRequestPayload(requestPayload);
        record.setStatus("PREPARED");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        try {
            BusinessAssert.isTrue(transactionRecordMapper.insertPrepared(record) == 1, "事务PREPARED记录写入失败");
        } catch (DuplicateKeyException duplicateKeyException) {
            // transaction_id 主键和“活跃 business_key”部分唯一索引都可能触发这里。
            // 先查再插仍有并发窗口，最终必须依赖数据库索引；ROLLED_BACK 不在索引谓词中，
            // 所以只有持久回滚完成后才会释放同一订单号的重试资格。
            BusinessException businessException = new BusinessException(
                    "订单号已有进行中或已提交的事务，不能重复开启");
            businessException.addSuppressed(duplicateKeyException);
            throw businessException;
        }
        return record;
    }

    /**
     * 明确失败在新事务中留下 ROLLED_BACK 事实，供 Broker 回查。
     *
     * <p>只有返回 {@code true} 才表示本次抢占了回滚终态；返回 {@code false} 可能是并发本地事务
     * 已提交，调用方必须重读。ROLLED_BACK 事务提交后，部分唯一索引才原子释放
     * businessKey，不存在“先删唯一键、后写回滚状态”的中间窗口。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean markRolledBack(String transactionId, String error) {
        String value = error == null || error.isBlank() ? "本地事务明确失败" : error;
        value = value.substring(0, Math.min(value.length(), 1000));
        return transactionRecordMapper.markRolledBack(transactionId, value) == 1;
    }

    /**
     * 短事务读取一批过期 PREPARED 候选，读取完成后立即释放快照。
     *
     * <p>返回的对象只是旧快照，不能据此直接宣告回滚；调度器必须再通过
     * {@link #markRolledBack(String, String)} 的条件更新逐条参与终态竞争。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager",
            propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<MqTransactionRecord> findExpiredPreparedCandidates(LocalDateTime expiredBefore, int batchSize) {
        BusinessAssert.notNull(expiredBefore, "事务清理截止时间不能为空");
        BusinessAssert.isTrue(batchSize > 0 && batchSize <= 1000, "事务清理批量必须在1到1000之间");
        return transactionRecordMapper.selectExpiredPreparedCandidates(expiredBefore, batchSize);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public MqTransactionRecord getById(String transactionId) {
        return transactionRecordMapper.selectById(transactionId);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public PageResult<MqTransactionRecord> page(String status, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        long offset = (long) (pageNum - 1) * pageSize;
        PageResult<MqTransactionRecord> result = new PageResult<>();
        result.setList(transactionRecordMapper.selectPage(status, offset, pageSize));
        result.setTotal(transactionRecordMapper.countPage(status));
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum > 0, "pageNum必须大于0");
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 100, "pageSize必须在1到100之间");
    }
}
