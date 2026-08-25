package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.xt.xiaoxingxing.playground.rocketmq.config.OrderMqConstants;
import com.xt.xiaoxingxing.playground.rocketmq.config.OrderMqProperties;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Order;
import com.xt.xiaoxingxing.playground.rocketmq.enums.OrderStatus;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.OrderMapper;
import com.xt.xiaoxingxing.playground.rocketmq.util.RocketMqUtil;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

/** 支付超时调度服务。 */
@Slf4j
@Service
public class PaymentTimeoutScheduler {

    private static final long MINIMUM_BROKER_DELAY_SECONDS = 1L;

    private final OrderMapper orderMapper;
    private final MqConsumerRecordMapper consumerRecordMapper;
    private final RocketMqUtil rocketMqUtil;
    private final OrderMqProperties properties;
    private final TransactionTemplate transactionTemplate;

    public PaymentTimeoutScheduler(OrderMapper orderMapper,
                                   MqConsumerRecordMapper consumerRecordMapper,
                                   RocketMqUtil rocketMqUtil,
                                   OrderMqProperties properties,
                                   @Qualifier("playgroundTransactionManager")
                                   PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.consumerRecordMapper = consumerRecordMapper;
        this.rocketMqUtil = rocketMqUtil;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 安排订单支付超时检查。 */
    public void schedule(String orderNo, String transactionId) {
        String consumerGroup = OrderMqConstants.CONSUMER_GROUP_TIMEOUT_SCHEDULER;
        transactionTemplate.executeWithoutResult(status -> {
            // 插入消费消息， 冥等判断
            MqConsumedMessage consumed = new MqConsumedMessage();
            consumed.setConsumerGroup(consumerGroup);
            consumed.setConsumeId(transactionId);
            consumed.setConsumedAt(LocalDateTime.now());
            if (consumerRecordMapper.insertConsumedIfAbsent(consumed) != 1) {
                return;
            }


            Order order = BusinessAssert.notNull(
                    orderMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
            if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
                log.info("事务订单已不是PENDING，跳过超时调度: orderNo={}, orderId={}, status={}",
                        orderNo, order.getId(), order.getStatus());
                return;
            }

            LocalDateTime deadline = order.getCreatedAt()
                    .plus(Duration.ofMillis(properties.getOrderTimeoutMillis()));
            long remainingMillis = Math.max(
                    0L, Duration.between(LocalDateTime.now(), deadline).toMillis());
            long delaySeconds = Math.max(
                    MINIMUM_BROKER_DELAY_SECONDS, (remainingMillis + 999L) / 1000L);

            SendReceipt receipt = rocketMqUtil.sendDelay(
                    OrderMqConstants.TOPIC_DELAY,
                    OrderMqConstants.TAG_PAYMENT_TIMEOUT_CHECK,
                    orderNo,
                    transactionId,
                    Duration.ofSeconds(delaySeconds));
            log.info("事务订单超时检查已安排: "
                            + "orderNo={}, sourceTransactionId={}, brokerMessageId={}, delaySeconds={}",
                    orderNo, transactionId, receipt.getMessageId(), delaySeconds);
        });
    }
}
