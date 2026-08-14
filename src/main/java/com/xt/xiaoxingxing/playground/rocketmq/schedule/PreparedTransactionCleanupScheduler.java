package com.xt.xiaoxingxing.playground.rocketmq.schedule;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.service.RocketTransactionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主动收口“已写 PREPARED，但进程在半消息到达 Broker 前崩溃”的孤儿事务。
 *
 * <p>Broker 只能回查它已经收到的半消息。如果应用在独立事务提交 PREPARED 后立即断电，
 * Broker 根本没有回查触发源，因此必须由数据库侧的主动调度补齐这个窄窗口。</p>
 *
 * <p>这个调度器不相信首次 SELECT 快照，也不会删除事务记录。它和 Broker checker 共用
 * {@code PREPARED -> ROLLED_BACK} 条件更新，再以更新结果与重读终态作为并发裁决。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PreparedTransactionCleanupScheduler {

    private static final String CLEANUP_REASON =
            "事务PREPARED超过保护窗口，主动清理器收口为ROLLED_BACK";

    private final RocketTransactionRecordService recordService;
    private final RocketMqLearningProperties properties;
    private final AtomicBoolean cleaning = new AtomicBoolean(false);

    /** 调度频率只影响过期后多久被发现；真正安全窗口由 transactionPreparedTimeoutSeconds 决定。 */
    @Scheduled(fixedDelayString = "${playground.rocketmq.transaction-cleanup.fixed-delay-millis}",
            initialDelayString = "${playground.rocketmq.transaction-cleanup.initial-delay-millis}")
    public void closeExpiredPreparedRecords() {
        // fixedDelay 默认不会在单线程调度器中重入，AtomicBoolean 仍作为未来改为多线程时的本进程防线。
        if (!cleaning.compareAndSet(false, true)) {
            log.debug("上一轮过期PREPARED清理仍在进行，本轮跳过");
            return;
        }

        try {
            long timeoutSeconds = properties.getTransactionPreparedTimeoutSeconds();
            if (timeoutSeconds <= 0) {
                // 负数截止时间会把新创建的 PREPARED 也误判为过期，因此不能悄悄容错。
                log.error("停止过期PREPARED清理: transactionPreparedTimeoutSeconds必须大于0，当前={}",
                        timeoutSeconds);
                return;
            }

            // 第1步：只依据与 checker 相同的保护窗口计算候选截止时间。
            LocalDateTime expiredBefore = LocalDateTime.now().minusSeconds(timeoutSeconds);
            List<MqTransactionRecord> candidates = recordService.findExpiredPreparedCandidates(
                    expiredBefore, properties.getTransactionCleanupBatchSize());

            // 第2步：候选列表只控制批量；每条记录的终态必须在独立短事务中重新竞争。
            for (MqTransactionRecord candidate : candidates) {
                closeOne(candidate);
            }
        } catch (Exception exception) {
            // 批量查询或配置异常不应让 Spring 取消以后调度；保留 PREPARED 下轮再试。
            log.error("过期PREPARED批量清理失败，未裁决记录将在后续调度重试", exception);
        } finally {
            cleaning.set(false);
        }
    }

    /**
     * 第3步，用持久状态裁决一条过期候选。
     *
     * <p>并发语义：</p>
     * <ol>
     *     <li>清理条件更新先成功：本地事务随后的 COMMITTED 更新必然为 0 行，订单与库存一起回滚；</li>
     *     <li>本地事务先提交：清理更新为 0 行，重读 COMMITTED 后仅记录日志；</li>
     *     <li>checker 或另一实例先回滚：清理更新为 0 行，重读 ROLLED_BACK 后按幂等成功处理；</li>
     *     <li>更新或重读失败：不猜测终态，保留数据库现状等待下轮。</li>
     * </ol>
     */
    private void closeOne(MqTransactionRecord candidate) {
        String transactionId = candidate.getTransactionId();
        try {
            if (recordService.markRolledBack(transactionId, CLEANUP_REASON)) {
                // 无半消息孤儿到此已完全收口；若 Broker 其实收到了半消息，之后 checker 会读到此终态并回滚半消息。
                log.warn("过期PREPARED主动收口成功: transactionId={}, businessKey={}, createdAt={}",
                        transactionId, candidate.getBusinessKey(), candidate.getCreatedAt());
                return;
            }

            // 第4步：0 行不等于回滚。只有重读的 COMMITTED/ROLLED_BACK 才是可信终态。
            MqTransactionRecord latest = recordService.getById(transactionId);
            if (latest != null && "COMMITTED".equals(latest.getStatus())) {
                log.info("过期PREPARED收口抢占失败，重读发现本地事务已提交: transactionId={}, orderId={}",
                        transactionId, latest.getOrderId());
                return;
            }
            if (latest != null && "ROLLED_BACK".equals(latest.getStatus())) {
                log.debug("过期PREPARED已由checker或其他实例收口: transactionId={}", transactionId);
                return;
            }
            log.warn("过期PREPARED收口抢占失败且重读无明确终态，保留现状等待下轮: transactionId={}, latestStatus={}",
                    transactionId, latest == null ? null : latest.getStatus());
        } catch (Exception exception) {
            // 单条失败不阻断同批其他孤儿；条件更新是幂等的，下轮可安全重试。
            log.error("过期PREPARED单条收口失败，保留现状等待后续调度: transactionId={}", transactionId, exception);
        }
    }
}
