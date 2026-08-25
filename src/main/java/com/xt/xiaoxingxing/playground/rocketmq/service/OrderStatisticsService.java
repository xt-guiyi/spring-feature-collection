package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.xt.xiaoxingxing.playground.rocketmq.config.OrderMqConstants;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Order;
import com.xt.xiaoxingxing.playground.rocketmq.enums.OrderOperation;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.OrderMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.OrderStatisticsMapper;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/** 订单统计服务。 */
@Service
public class OrderStatisticsService {

    private final MqConsumerRecordMapper consumerRecordMapper;
    private final OrderMapper orderMapper;
    private final OrderStatisticsMapper orderStatisticsMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderStatisticsService(MqConsumerRecordMapper consumerRecordMapper,
                                  OrderMapper orderMapper,
                                  OrderStatisticsMapper orderStatisticsMapper,
                                  @Qualifier("playgroundTransactionManager")
                                  PlatformTransactionManager transactionManager) {
        this.consumerRecordMapper = consumerRecordMapper;
        this.orderMapper = orderMapper;
        this.orderStatisticsMapper = orderStatisticsMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 记录订单统计。 */
    public void record(String transactionId, OrderOperation operation, String orderNo) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime consumedAt = LocalDateTime.now();
            MqConsumedMessage consumed = new MqConsumedMessage();
            consumed.setConsumerGroup(OrderMqConstants.CONSUMER_GROUP_ORDER_STATISTICS);
            consumed.setConsumeId(transactionId);
            consumed.setConsumedAt(consumedAt);
            if (consumerRecordMapper.insertConsumedIfAbsent(consumed) != 1) {
                return;
            }

            Order order = BusinessAssert.notNull(
                    orderMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
            orderStatisticsMapper.upsert(
                    operation.name(), order.getTotalAmount(), consumedAt);
        });
    }
}
