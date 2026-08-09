package com.xt.xiaoxingxing.playground.rabbitmq.service;

import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqNotificationLog;
import com.xt.xiaoxingxing.playground.rabbitmq.entity.MqOrderStatistics;
import com.xt.xiaoxingxing.playground.rabbitmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 提供只读观察数据，便于学习时核对幂等、统计和通知是否发生。 */
@Service
@RequiredArgsConstructor
public class RabbitMqOperationsService {

    private final MqConsumerRecordMapper consumerRecordMapper;

    public PageResult<MqConsumedMessage> pageConsumed(String consumerName, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        long offset = (long) (pageNum - 1) * pageSize;
        PageResult<MqConsumedMessage> result = new PageResult<>();
        result.setList(consumerRecordMapper.selectConsumedPage(consumerName, offset, pageSize));
        result.setTotal(consumerRecordMapper.countConsumed(consumerName));
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    public MqOrderStatistics getStatistics() {
        MqOrderStatistics statistics = consumerRecordMapper.selectStatistics();
        if (statistics != null) {
            return statistics;
        }

        // 尚未消费任何订单事件时也返回结构稳定的零值对象，前端不需要判断 data=null。
        statistics = new MqOrderStatistics();
        statistics.setId((short) 1);
        statistics.setCreatedCount(0L);
        statistics.setPaidCount(0L);
        statistics.setCancelledCount(0L);
        statistics.setCreatedAmount(BigDecimal.ZERO);
        return statistics;
    }

    public PageResult<MqNotificationLog> pageNotifications(int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        long offset = (long) (pageNum - 1) * pageSize;
        PageResult<MqNotificationLog> result = new PageResult<>();
        result.setList(consumerRecordMapper.selectNotificationPage(offset, pageSize));
        result.setTotal(consumerRecordMapper.countNotifications());
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum > 0, "pageNum必须大于0");
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 100, "pageSize必须在1到100之间");
    }
}
