package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqNotificationLog;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOrderStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** RocketMQ 消费幂等、统计与模拟通知的 PostgreSQL 接口。 */
@Mapper
public interface MqConsumerRecordMapper {

    /** @return 1 表示首次领取消息，0 表示唯一约束判定为重复消息。 */
    int insertConsumedIfAbsent(MqConsumedMessage consumedMessage);

    /** @return UPSERT 影响 1 行；前提是本次消息已首次领取。 */
    int upsertStatistics(@Param("eventType") String eventType,
                         @Param("totalAmount") BigDecimal totalAmount,
                         @Param("occurredAt") LocalDateTime occurredAt);

    /**
     * @return 0 表示相同消息和渠道已存在。正常重投会先被消费幂等记录挡住；若已领取本组消息却仍返回 0，
     *         说明数据被其他路径写入，当前案例把它作为一致性冲突回滚，而不是悄悄确认成功
     */
    int insertNotification(MqNotificationLog notificationLog);

    List<MqConsumedMessage> selectConsumedPage(@Param("consumerName") String consumerName,
                                               @Param("offset") long offset,
                                               @Param("pageSize") int pageSize);

    long countConsumed(@Param("consumerName") String consumerName);

    MqOrderStatistics selectStatistics();

    List<MqNotificationLog> selectNotificationPage(@Param("offset") long offset,
                                                   @Param("pageSize") int pageSize);

    long countNotifications();
}
