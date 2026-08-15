package com.xt.xiaoxingxing.playground.rocketmq.infrastructure;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.core.RocketMQTransactionChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Broker 事务回查器：只依据 PostgreSQL 中已经持久化的事务记录裁决半消息。
 *
 * <p>信封 {@code messageId} 就是事务记录主键；订单事务记录使用
 * {@code businessType=ORDER、businessKey=orderNo}，CREATE/PAY/CANCEL 信封的 aggregateId
 * 也全部使用相同 orderNo。这里不读取自定义 Header，也不依赖 JVM 内存状态。</p>
 */
@Slf4j
@Component("orderTransactionChecker")
@RequiredArgsConstructor
@RocketMQTransactionListener
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
public class OrderTransactionChecker implements RocketMQTransactionChecker {

    private final TransactionRecordRepository transactionRecordRepository;
    private final RocketMessageCodec messageCodec;
    private final RocketMqLearningProperties properties;

    /**
     * 实现步骤：
     * <ol>
     *     <li>第1步：解码并校验版本化信封，直接取 envelope.messageId 作为 transactionId；</li>
     *     <li>第2步：查询事务记录，并校验操作类型、事件类型与真实 aggregateId；</li>
     *     <li>第3步：COMMITTED/ROLLED_BACK 直接返回对应终态；</li>
     *     <li>第4步：保护窗口内的 PREPARED 返回 UNKNOWN，给本地事务继续执行的时间；</li>
     *     <li>第5步：过期 PREPARED 先条件抢占 ROLLED_BACK，成功后才向 Broker 返回 ROLLBACK；</li>
     *     <li>第6步：抢占失败必须重读，按并发赢家的持久终态裁决。</li>
     * </ol>
     */
    @Override
    public TransactionResolution check(MessageView messageView) {
        // 第1步：协议只维护一个业务 UUID，杜绝 Header、aggregateId 和记录主键三份关联值漂移。
        RocketMessageEnvelope<?> envelope = messageCodec.decode(messageView);
        String transactionId = envelope.getMessageId();

        // 第2步：记录不存在时不能猜测本地事务失败，保守 UNKNOWN 让 Broker 后续继续回查。
        MqTransactionRecord record = transactionRecordRepository.findById(transactionId);
        if (record == null) {
            log.error("事务回查找不到持久记录: brokerMessageId={}, transactionId={}",
                    messageView.getMessageId(), transactionId);
            return TransactionResolution.UNKNOWN;
        }
        if (!messageMatchesRecord(envelope, record)) {
            return TransactionResolution.UNKNOWN;
        }

        // 第3步：终态是 PostgreSQL 已提交事实，回查不再修改业务订单。
        if ("COMMITTED".equals(record.getStatus())) {
            log.info("事务回查提交: brokerMessageId={}, transactionId={}, businessType={}, businessKey={}, operationType={}",
                    messageView.getMessageId(), transactionId, record.getBusinessType(),
                    record.getBusinessKey(), record.getOperationType());
            return TransactionResolution.COMMIT;
        }
        if ("ROLLED_BACK".equals(record.getStatus())) {
            log.info("事务回查回滚: brokerMessageId={}, transactionId={}, operationType={}",
                    messageView.getMessageId(), transactionId, record.getOperationType());
            return TransactionResolution.ROLLBACK;
        }
        if (!"PREPARED".equals(record.getStatus())) {
            log.error("事务回查发现未知持久状态，保守返回UNKNOWN: transactionId={}, status={}",
                    transactionId, record.getStatus());
            return TransactionResolution.UNKNOWN;
        }

        // 第4步：保护窗口必须覆盖正常本地订单事务时长，避免 Checker 过早回滚仍在执行的事务。
        LocalDateTime deadline = record.getCreatedAt()
                .plusSeconds(properties.getTransactionPreparedTimeoutSeconds());
        if (LocalDateTime.now().isBefore(deadline)) {
            log.info("事务仍在PREPARED保护窗口，等待下次回查: transactionId={}", transactionId);
            return TransactionResolution.UNKNOWN;
        }

        // 第5步：不能只向 Broker 返回 ROLLBACK 而让数据库继续 PREPARED。条件更新先赢时，后续本地
        // markCommitted 会返回 false，订单事实所在事务必须整体回滚；本地事务先赢时本次更新为0行。
        if (transactionRecordRepository.markRolledBack(
                transactionId, "事务PREPARED超过保护窗口，Broker回查裁决回滚")) {
            log.warn("事务PREPARED已过期，Broker回查抢占ROLLED_BACK成功: transactionId={}", transactionId);
            return TransactionResolution.ROLLBACK;
        }

        // 第6步：首次读取已经过时，必须重新查询持久事实，不能沿用旧 PREPARED 快照。
        MqTransactionRecord latest = transactionRecordRepository.findById(transactionId);
        if (latest != null && "COMMITTED".equals(latest.getStatus())) {
            log.info("事务回查抢占回滚失败，重读发现本地事务已提交: transactionId={}, businessType={}, businessKey={}",
                    transactionId, latest.getBusinessType(), latest.getBusinessKey());
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

    private boolean messageMatchesRecord(RocketMessageEnvelope<?> envelope, MqTransactionRecord record) {
        String expectedEventType;
        try {
            if (!RocketMqNames.BUSINESS_ORDER.equals(record.getBusinessType())) {
                throw new IllegalArgumentException("非订单业务类型");
            }
            expectedEventType = switch (record.getOperationType()) {
                case RocketMqNames.OPERATION_CREATE -> RocketMqNames.EVENT_ORDER_CREATED;
                case RocketMqNames.OPERATION_PAY -> RocketMqNames.EVENT_ORDER_PAID;
                case RocketMqNames.OPERATION_CANCEL -> RocketMqNames.EVENT_ORDER_CANCELLED;
                default -> throw new IllegalArgumentException("未知operationType");
            };
        } catch (IllegalArgumentException exception) {
            log.error("订单事务回查记录业务类型或operationType非法，保守返回UNKNOWN: transactionId={}, "
                            + "businessType={}, businessKey={}, operationType={}",
                    record.getTransactionId(), record.getBusinessType(), record.getBusinessKey(),
                    record.getOperationType(), exception);
            return false;
        }
        if (!expectedEventType.equals(envelope.getEventType())
                || record.getBusinessKey() == null
                || !record.getBusinessKey().equals(envelope.getAggregateId())) {
            log.error("订单事务回查消息与记录不一致: transactionId={}, businessType={}, businessKey={}, "
                            + "operationType={}, expectedEventType={}, actualEventType={}, actualAggregateId={}",
                    record.getTransactionId(), record.getBusinessType(), record.getBusinessKey(),
                    record.getOperationType(), expectedEventType, envelope.getEventType(), envelope.getAggregateId());
            return false;
        }
        return true;
    }
}
