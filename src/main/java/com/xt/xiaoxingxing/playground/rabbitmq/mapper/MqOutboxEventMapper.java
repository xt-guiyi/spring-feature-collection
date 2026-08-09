package com.xt.xiaoxingxing.playground.rabbitmq.mapper;

import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Transactional Outbox 的写入、原子认领、状态推进和观察查询。 */
@Mapper
public interface MqOutboxEventMapper {

    int insert(MqOutboxEvent event);

    List<MqOutboxEvent> claimPublishable(@Param("batchSize") int batchSize,
                                         @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore);

    int markPublished(@Param("id") String id);

    int markFailed(@Param("id") String id,
                   @Param("lastError") String lastError,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("maxPublishRetries") int maxPublishRetries);

    MqOutboxEvent selectById(@Param("id") String id);

    List<MqOutboxEvent> selectPage(@Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("pageSize") int pageSize);

    long countPage(@Param("status") String status);
}
