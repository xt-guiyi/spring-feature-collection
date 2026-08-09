package com.xt.xiaoxingxing.playground.rabbitmq.mapper;

import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqNotificationLog;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOrderStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 消费幂等、统计和模拟通知的 PostgreSQL Mapper。 */
@Mapper
public interface MqConsumerRecordMapper {

    int insertConsumedIfAbsent(MqConsumedMessage consumedMessage);

    int upsertStatistics(@Param("eventType") String eventType,
                         @Param("totalAmount") BigDecimal totalAmount,
                         @Param("occurredAt") LocalDateTime occurredAt);

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
