package com.xt.xiaoxingxing.playground.rocketmq.infrastructure;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主动收口 PREPARED 已提交、但应用在半消息真正到达 Broker 前崩溃留下的孤儿记录。
 *
 * <p>Broker 只能回查自己已经收到的半消息，因此上述窗口没有 Broker 触发源。数据库任务不能准确知道
 * 半消息是否到达，只能扫描超过保护窗口的 PREPARED；它与 Checker、本地业务事务统一使用同一条
 * PREPARED 条件终态更新，即使多个路径同时发现同一记录，也只有一个路径能赢得状态变更。</p>
 */
@Slf4j
@Component("preparedTransactionCleanupTask")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PreparedTransactionCleanupTask {

    private static final String CLEANUP_REASON =
            "事务PREPARED超过保护窗口，主动清理任务收口为ROLLED_BACK";

    private final TransactionRecordRepository transactionRecordRepository;
    private final RocketMqLearningProperties properties;
    private final AtomicBoolean cleaning = new AtomicBoolean(false);

    /**
     * 实现步骤：
     * <ol>
     *     <li>第1步：使用本进程原子开关避免调度线程重入；</li>
     *     <li>第2步：按照与 Broker Checker 相同的保护窗口计算过期时间；</li>
     *     <li>第3步：短事务只读取有限数量的 PREPARED 候选快照；</li>
     *     <li>第4步：逐条通过独立短事务条件更新竞争 ROLLED_BACK；</li>
     *     <li>第5步：更新0行时重读并尊重并发赢家，不覆盖 COMMITTED。</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${playground.rocketmq.transaction-cleanup.fixed-delay-millis}",
            initialDelayString = "${playground.rocketmq.transaction-cleanup.initial-delay-millis}")
    public void closeExpiredPreparedRecords() {
        // 第1步：当前默认调度器通常单线程，AtomicBoolean 仍为以后切换多线程调度预留本进程保护。
        if (!cleaning.compareAndSet(false, true)) {
            log.debug("上一轮过期PREPARED清理仍在进行，本轮跳过");
            return;
        }

        try {
            // 第2步：保护窗口配置错误时停止本轮，绝不能把刚创建的 PREPARED 误判成孤儿。
            long timeoutSeconds = properties.getTransactionPreparedTimeoutSeconds();
            if (timeoutSeconds <= 0) {
                log.error("停止过期PREPARED清理: transactionPreparedTimeoutSeconds必须大于0，当前={}",
                        timeoutSeconds);
                return;
            }
            LocalDateTime expiredBefore = LocalDateTime.now().minusSeconds(timeoutSeconds);

            // 第3步：SELECT 结果只是候选快照。读取后不持有跨循环行锁，避免一批清理占用长事务。
            List<MqTransactionRecord> candidates = transactionRecordRepository.findExpiredPrepared(
                    expiredBefore, properties.getTransactionCleanupBatchSize());

            // 第4步：每条记录在独立短事务中条件竞争，单条失败不会阻断同批其他孤儿。
            for (MqTransactionRecord candidate : candidates) {
                closeOne(candidate);
            }
        } catch (Exception exception) {
            log.error("过期PREPARED批量清理失败，未裁决记录将在后续调度重试", exception);
        } finally {
            cleaning.set(false);
        }
    }

    /**
     * 用持久状态裁决一条过期候选。
     *
     * <p>并发结果：</p>
     * <ol>
     *     <li>清理先赢：本地业务事务的 COMMITTED 条件更新为0，业务数据必须整体回滚；</li>
     *     <li>本地事务先赢：清理更新为0，重读 COMMITTED 后只记录日志；</li>
     *     <li>Checker/另一实例先回滚：重读 ROLLED_BACK，按幂等成功处理；</li>
     *     <li>重读仍无终态：保留现状，等待下一轮或 Broker 再次回查。</li>
     * </ol>
     */
    private void closeOne(MqTransactionRecord candidate) {
        String transactionId = candidate.getTransactionId();
        try {
            if (transactionRecordRepository.markRolledBack(transactionId, CLEANUP_REASON)) {
                log.warn("过期PREPARED主动收口成功: transactionId={}, businessType={}, businessKey={}, "
                                + "operationType={}, createdAt={}",
                        transactionId, candidate.getBusinessType(), candidate.getBusinessKey(),
                        candidate.getOperationType(), candidate.getCreatedAt());
                return;
            }

            // 第5步：条件更新0行绝不等于“仍应回滚”，必须读取并发路径已经提交的真实状态。
            MqTransactionRecord latest = transactionRecordRepository.findById(transactionId);
            if (latest != null && "COMMITTED".equals(latest.getStatus())) {
                log.info("过期PREPARED收口抢占失败，重读发现本地事务已提交: transactionId={}, "
                                + "businessType={}, businessKey={}, operationType={}",
                        transactionId, latest.getBusinessType(), latest.getBusinessKey(),
                        latest.getOperationType());
                return;
            }
            if (latest != null && "ROLLED_BACK".equals(latest.getStatus())) {
                log.debug("过期PREPARED已由Checker或其他实例收口: transactionId={}", transactionId);
                return;
            }
            log.warn("过期PREPARED收口抢占失败且重读无明确终态，保留现状等待下轮: "
                            + "transactionId={}, latestStatus={}",
                    transactionId, latest == null ? null : latest.getStatus());
        } catch (Exception exception) {
            log.error("过期PREPARED单条收口失败，保留现状等待后续调度: transactionId={}",
                    transactionId, exception);
        }
    }
}
