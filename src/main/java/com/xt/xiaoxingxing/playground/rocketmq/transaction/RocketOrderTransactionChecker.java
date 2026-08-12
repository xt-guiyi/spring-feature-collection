package com.xt.xiaoxingxing.playground.rocketmq.transaction;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.service.RocketTransactionRecordService;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.core.RocketMQTransactionChecker;

import java.time.LocalDateTime;

/** Broker 事务回查器：只依赖 PostgreSQL 持久记录裁决，不读取 JVM 内存变量。 */
@Slf4j
@RequiredArgsConstructor
@RocketMQTransactionListener
public class RocketOrderTransactionChecker implements RocketMQTransactionChecker {

    private final RocketTransactionRecordService recordService;
    private final RocketMessageCodec messageCodec;
    private final RocketMqLearningProperties properties;

    /**
     * 实现步骤：
     * 第1步，从消息属性或版本化信封取得 transactionId；
     * 第2步，只读查询持久化事务记录；
     * 第3步，COMMITTED/ROLLED_BACK 返回明确结论；
     * 第4步，保护窗口内 PREPARED 返回 UNKNOWN；
     * 第5步，过期且无订单事实时，用独立事务条件更新抢占 ROLLED_BACK 终态；
     * 第6步，抢占失败时重新读取终态，再决定 COMMIT、ROLLBACK 或 UNKNOWN。
     */
    @Override
    public TransactionResolution check(MessageView messageView) {
        // 第1步：属性便于快速关联；信封 aggregateId 是兼容兜底，回查不依赖原 HTTP 请求或原 JVM。
        String transactionId = messageView.getProperties().get(RocketMqNames.HEADER_TRANSACTION_ID);
        if (transactionId == null || transactionId.isBlank()) {
            RocketMessageEnvelope<?> envelope = messageCodec.decode(messageView);
            transactionId = envelope.getAggregateId();
        }

        // 第2步：记录不存在时不能猜测本地事务成功，保守返回 UNKNOWN 让 Broker 后续再查并保留证据。
        MqTransactionRecord record = recordService.getById(transactionId);
        if (record == null) {
            log.error("事务回查找不到持久记录: brokerMessageId={}, transactionId={}",
                    messageView.getMessageId(), transactionId);
            return TransactionResolution.UNKNOWN;
        }

        // 第3步：这两个状态是 PostgreSQL 已持久化事实；回查本身不修改业务记录。
        if ("COMMITTED".equals(record.getStatus())) {
            log.info("事务回查提交: brokerMessageId={}, transactionId={}, orderId={}",
                    messageView.getMessageId(), transactionId, record.getOrderId());
            return TransactionResolution.COMMIT;
        }
        if ("ROLLED_BACK".equals(record.getStatus())) {
            log.info("事务回查回滚: brokerMessageId={}, transactionId={}", messageView.getMessageId(), transactionId);
            return TransactionResolution.ROLLBACK;
        }

        // 第4步：合理执行窗口内返回 UNKNOWN，避免本地事务仍在运行时被过早回滚。
        LocalDateTime deadline = record.getCreatedAt()
                .plusSeconds(properties.getTransactionPreparedTimeoutSeconds());
        if (LocalDateTime.now().isBefore(deadline)) {
            log.info("事务仍在PREPARED保护窗口，等待下次回查: transactionId={}", transactionId);
            return TransactionResolution.UNKNOWN;
        }
        if (record.getOrderId() != null) {
            log.warn("事务PREPARED已过期但关联订单事实不为空，保守返回UNKNOWN等待人工核对: transactionId={}", transactionId);
            return TransactionResolution.UNKNOWN;
        }

        // 第5步：不能只向 Broker 返回 ROLLBACK 而让数据库仍停在 PREPARED。条件更新是回查与本地事务的
        // 并发裁决点：回查先赢时，本地 markCommitted 会影响 0 行并让整个订单事务回滚；本地事务先赢时，
        // 本次更新影响 0 行，绝不能再用刚才读到的旧 PREPARED 快照决定回滚。
        if (recordService.markRolledBack(transactionId, "事务PREPARED超过保护窗口，Broker回查裁决回滚")) {
            log.warn("事务PREPARED已过期且回查抢占ROLLED_BACK终态成功: transactionId={}", transactionId);
            return TransactionResolution.ROLLBACK;
        }

        // 第6步：条件更新失败说明终态可能已由并发本地事务确定，必须重新读取数据库事实。
        MqTransactionRecord latest = recordService.getById(transactionId);
        if (latest != null && "COMMITTED".equals(latest.getStatus())) {
            log.info("事务回查抢占回滚失败，重读发现本地事务已提交: transactionId={}, orderId={}",
                    transactionId, latest.getOrderId());
            return TransactionResolution.COMMIT;
        }
        if (latest != null && "ROLLED_BACK".equals(latest.getStatus())) {
            log.info("事务回查抢占回滚失败，重读发现事务已回滚: transactionId={}", transactionId);
            return TransactionResolution.ROLLBACK;
        }
        log.warn("事务回查抢占回滚失败且未读到明确终态，保守返回UNKNOWN: transactionId={}, latestStatus={}",
                transactionId, latest == null ? null : latest.getStatus());
        return TransactionResolution.UNKNOWN;
    }
}
