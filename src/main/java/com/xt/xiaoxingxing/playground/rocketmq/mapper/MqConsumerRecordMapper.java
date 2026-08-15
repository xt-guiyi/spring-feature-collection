package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/** RocketMQ 消费幂等与订单统计投影的 PostgreSQL 接口。 */
@Mapper
public interface MqConsumerRecordMapper {

    /** 只判断指定消费组是否已经完成该业务消息；用于事务外发送前的短只读检查。 */
    boolean existsConsumed(@Param("consumerName") String consumerName,
                           @Param("messageId") String messageId);

    /** @return 1 表示首次领取消息，0 表示唯一约束判定为重复消息。 */
    int insertConsumedIfAbsent(MqConsumedMessage consumedMessage);

    /** @return UPSERT 影响 1 行；前提是本次消息已首次领取。 */
    int upsertStatistics(@Param("eventType") String eventType,
                         @Param("totalAmount") BigDecimal totalAmount,
                         @Param("occurredAt") LocalDateTime occurredAt);
}
