package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 的写入、原子领取和状态推进接口。
 *
 * <p>所有更新方法都返回受影响行数；调用方必须把 0 行视为状态已被其他工作线程推进，
 * 不能把它误当成可再次覆盖的成功。</p>
 */
@Mapper
public interface MqOutboxEventMapper {

    int insert(MqOutboxEvent event);

    List<MqOutboxEvent> claimPublishable(@Param("batchSize") int batchSize,
                                         @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore);

    /**
     * 仅允许持有当前领取租约的 worker 标记发布成功。
     *
     * @param claimedLockedAt {@link #claimPublishable(int, LocalDateTime)} 返回的 lockedAt；租约过期并被重新领取后，
     *                        旧 worker 携带的时间已失效，本更新必须影响 0 行
     */
    int markPublished(@Param("id") String id,
                      @Param("claimedLockedAt") LocalDateTime claimedLockedAt);

    int markFailed(@Param("id") String id,
                   @Param("claimedLockedAt") LocalDateTime claimedLockedAt,
                   @Param("lastError") String lastError,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("maxPublishRetries") int maxPublishRetries);

}
