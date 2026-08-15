package com.xt.xiaoxingxing.playground.rocketmq.order.transaction.listener;

import tools.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.infrastructure.TransactionRecordRepository;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.transaction.OrderService;
import com.xt.xiaoxingxing.playground.rocketmq.support.MessageDecodingException;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketConsumerSupport;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 已提交 CREATE 事实的付款超时调度 Listener。
 *
 * <p>本类只验证消息边界并提取事务记录中的 {@code orderNo + createdMessageId}。延迟截止时间、稳定 timeout ID、
 * 重复发送窗口均由事务版 {@link OrderService#schedulePaymentTimeout(String, String)} 统一处理。</p>
 */
@Component("transactionPaymentTimeoutScheduleListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.transaction}",
        consumerGroup = "${playground.rocketmq.consumer-groups.transaction-timeout-scheduler}",
        tag = "${playground.rocketmq.tags.order-created}")
public class PaymentTimeoutScheduleListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final TransactionRecordRepository transactionRecordRepository;
    private final OrderService orderService;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getTransactionTimeoutScheduler(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：调度组只接受事务 Topic 中已经提交的 ORDER_CREATED 事实。
        if (!properties.getTags().getOrderCreated().equals(actualTag)
                || !RocketMqNames.EVENT_ORDER_CREATED.equals(envelope.getEventType())) {
            throw new MessageDecodingException(
                    "事务超时调度消息Tag与eventType不匹配: tag=" + actualTag
                            + ", eventType=" + envelope.getEventType());
        }

        // 第2步：以 messageId 查询事务记录，确认它是已提交的 ORDER/CREATE，且 businessKey 与 aggregateId 一致。
        MqTransactionRecord record = BusinessAssert.notNull(
                transactionRecordRepository.findById(envelope.getMessageId()), "CREATE事务记录不存在");
        BusinessAssert.isTrue(envelope.getMessageId().equals(record.getTransactionId())
                        && RocketMqNames.OPERATION_CREATE.equals(record.getOperationType())
                        && "COMMITTED".equals(record.getStatus())
                        && RocketMqNames.BUSINESS_ORDER.equals(record.getBusinessType())
                        && record.getBusinessKey() != null
                        && !record.getBusinessKey().isBlank()
                        && record.getBusinessKey().equals(envelope.getAggregateId()),
                "CREATE消息与已提交事务记录不一致");

        // 第3步：只传业务调度所需参数，不把 Tag、MessageView、信封或命令 payload 传进业务 Service。
        orderService.schedulePaymentTimeout(record.getBusinessKey(), envelope.getMessageId());
    }
}
