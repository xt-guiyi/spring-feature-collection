package com.xt.xiaoxingxing.playground.features.rocketmq.task;

import com.xt.xiaoxingxing.playground.features.rocketmq.config.OrderMqProperties;
import com.xt.xiaoxingxing.playground.features.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.features.rocketmq.repository.TransactionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 过期事务记录清理任务。 */
@Slf4j
@Component("preparedTransactionCleanupTask")
@RequiredArgsConstructor
public class PreparedTransactionCleanupTask {

    private static final long CLEANUP_INTERVAL_MILLIS = 30_000L;
    private static final String CLEANUP_REASON =
            "事务PREPARED超过保护窗口，主动清理任务收口为ROLLED_BACK";

    private final TransactionRecordRepository transactionRecordRepository;
    private final OrderMqProperties properties;

    /** 清理过期的事务记录。 */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MILLIS, initialDelay = CLEANUP_INTERVAL_MILLIS)
    public void closeExpiredPreparedRecords() {
        try {
            LocalDateTime expiredBefore = LocalDateTime.now()
                    .minusSeconds(properties.getTransactionPreparedTimeoutSeconds());

            List<MqTransactionRecord> candidates = transactionRecordRepository.findExpiredPrepared(
                    expiredBefore, properties.getTransactionCleanupBatchSize());

            for (MqTransactionRecord candidate : candidates) {
                closeOne(candidate);
            }
        } catch (Exception exception) {
            log.error("过期PREPARED批量清理失败，未裁决记录将在后续调度重试", exception);
        }
    }

    /** 清理一条过期事务记录。 */
    private void closeOne(MqTransactionRecord candidate) {
        String transactionId = candidate.getTransactionId();
        try {
            if (transactionRecordRepository.markRolledBack(transactionId, CLEANUP_REASON)) {
                log.warn("过期PREPARED主动收口成功: transactionId={}, createdAt={}",
                        transactionId, candidate.getCreatedAt());
                return;
            }
            log.debug("过期PREPARED已由其他并发路径裁决: transactionId={}", transactionId);
        } catch (Exception exception) {
            log.error("过期PREPARED单条收口失败，保留现状等待后续调度: transactionId={}",
                    transactionId, exception);
        }
    }
}
