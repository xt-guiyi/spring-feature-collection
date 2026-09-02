package com.xt.xiaoxingxing.playground.features.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.features.rocketmq.entity.MqConsumedMessage;
import org.apache.ibatis.annotations.Mapper;

/** 消息消费记录数据访问接口。 */
@Mapper
public interface MqConsumerRecordMapper {

    /** 新增消息消费记录。 */
    int insertConsumedIfAbsent(MqConsumedMessage consumedMessage);

}
