package com.xt.xiaoxingxing.playground.features.rocketmq.checker;

import com.xt.xiaoxingxing.playground.features.rocketmq.config.OrderMqProperties;
import com.xt.xiaoxingxing.playground.features.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.features.rocketmq.enums.MqTransactionStatus;
import com.xt.xiaoxingxing.playground.features.rocketmq.repository.TransactionRecordRepository;
import com.xt.xiaoxingxing.playground.features.rocketmq.util.RocketMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.core.RocketMQTransactionChecker;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 订单事务消息检查器。 */
@Slf4j
@Component("orderTransactionChecker")
@RequiredArgsConstructor
@RocketMQTransactionListener
public class OrderTransactionChecker implements RocketMQTransactionChecker {

    private final TransactionRecordRepository transactionRecordRepository;
    private final RocketMessageCodec messageCodec;
    private final OrderMqProperties properties;

    /** 检查事务消息状态。 */
    @Override
    public TransactionResolution check(MessageView messageView) {
        String transactionId = messageCodec.decodeTransactionId(messageView);
        MqTransactionRecord record = transactionRecordRepository.findById(transactionId);
        if (record == null) {
            log.error("事务回查找不到持久记录: brokerMessageId={}, transactionId={}",
                    messageView.getMessageId(), transactionId);
            return TransactionResolution.UNKNOWN;
        }

        if (record.getStatus() == MqTransactionStatus.COMMITTED) {
            log.info("事务回查提交: brokerMessageId={}, transactionId={}",
                    messageView.getMessageId(), transactionId);
            return TransactionResolution.COMMIT;
        }
        if (record.getStatus() == MqTransactionStatus.ROLLED_BACK) {
            log.info("事务回查回滚: brokerMessageId={}, transactionId={}",
                    messageView.getMessageId(), transactionId);
            return TransactionResolution.ROLLBACK;
        }

        LocalDateTime deadline = record.getCreatedAt()
                .plusSeconds(properties.getTransactionPreparedTimeoutSeconds());
        if (LocalDateTime.now().isBefore(deadline)) {
            log.info("事务仍在PREPARED保护窗口，等待下次回查: transactionId={}", transactionId);
            return TransactionResolution.UNKNOWN;
        }

        if (transactionRecordRepository.markRolledBack(
                transactionId, "事务PREPARED超过保护窗口，Broker回查裁决回滚")) {
            log.warn("事务PREPARED已过期，Broker回查抢占ROLLED_BACK成功: transactionId={}", transactionId);
            return TransactionResolution.ROLLBACK;
        }

        MqTransactionRecord latest = transactionRecordRepository.findById(transactionId);
        if (latest != null && latest.getStatus() == MqTransactionStatus.COMMITTED) {
            log.info("事务回查抢占回滚失败，重读发现本地事务已提交: transactionId={}", transactionId);
            return TransactionResolution.COMMIT;
        }
        if (latest != null && latest.getStatus() == MqTransactionStatus.ROLLED_BACK) {
            log.info("事务回查抢占回滚失败，重读发现事务已回滚: transactionId={}", transactionId);
            return TransactionResolution.ROLLBACK;
        }
        log.warn("事务回查抢占回滚失败且未读到明确终态，保守返回UNKNOWN: transactionId={}, latestStatus={}",
                transactionId, latest == null ? null : latest.getStatus());
        return TransactionResolution.UNKNOWN;
    }
}
