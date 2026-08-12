package com.xt.xiaoxingxing.playground.rocketmq.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.EVENT_DEMO_MESSAGE;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.EVENT_ORDER_CANCELLED;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.EVENT_ORDER_CREATED;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.EVENT_ORDER_PAID;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.EVENT_ORDER_PAYMENT_TIMEOUT_CHECK;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.EVENT_TRANSACTION_ORDER_CREATED;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.TAG_DEMO;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.TAG_ORDER_CANCELLED;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.TAG_ORDER_CREATED;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.TAG_ORDER_PAID;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.TAG_ORDER_TIMEOUT;
import static com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames.TAG_RETRY;

/**
 * 所有监听器共用的消费生命周期模板。
 *
 * <p>该模板不在 Java 里制造重试副本。业务抛异常时返回 FAILURE，由 RocketMQ Broker 管理重新投递次数和
 * DLQ 路由；因此各消费者只需专注于“协议与路由契约通过且业务事务完成前绝不返回成功”
 * 和数据库幂等。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketConsumerSupport {

    /** DEMO Tag 只能携带 DEMO_MESSAGE 事件，NORMAL/FIFO/DELAY 演示组共用。 */
    public static final Map<String, String> DEMO_ROUTE_CONTRACT = Map.of(
            TAG_DEMO, EVENT_DEMO_MESSAGE);

    /** RETRY_DEMO Tag 仍是演示负载，重试只是消费失败策略，不是新事件类型。 */
    public static final Map<String, String> RETRY_ROUTE_CONTRACT = Map.of(
            TAG_RETRY, EVENT_DEMO_MESSAGE);

    /** 普通订单 Topic 的三个 Tag 必须与同名业务事件一一对应。 */
    public static final Map<String, String> ORDER_EVENT_ROUTE_CONTRACT = Map.of(
            TAG_ORDER_CREATED, EVENT_ORDER_CREATED,
            TAG_ORDER_PAID, EVENT_ORDER_PAID,
            TAG_ORDER_CANCELLED, EVENT_ORDER_CANCELLED);

    /** 超时 Tag 只允许进入“重新检查付款状态”处理器。 */
    public static final Map<String, String> ORDER_TIMEOUT_ROUTE_CONTRACT = Map.of(
            TAG_ORDER_TIMEOUT, EVENT_ORDER_PAYMENT_TIMEOUT_CHECK);

    /** 事务 Topic 复用 ORDER_CREATED Tag，但信封必须是独立的事务事件类型。 */
    public static final Map<String, String> TRANSACTION_ORDER_ROUTE_CONTRACT = Map.of(
            TAG_ORDER_CREATED, EVENT_TRANSACTION_ORDER_CREATED);

    private final RocketMessageCodec codec;

    /**
     * 第1步解码并校验信封协议；第2步校验 MessageView 的实际 Tag 与信封 eventType 一致；
     * 第3步调用独立 Spring Service 执行业务本地事务；第4步仅在其正常返回后确认 SUCCESS。
     *
     * <p>Tag 是 Broker 过滤契约，eventType 是 JSON 业务协议，两者必须同时可信。如果只相信 Tag，
     * {@code ORDER_PAYMENT_TIMEOUT_CHECK} Tag 内伪装的其他 eventType 可能误触发取消订单；如果只相信
     * eventType，同一条消息在多消费组中可能产生分裂语义。因此必须在任何业务副作用之前校验映射。</p>
     *
     * <p>任何异常均返回 FAILURE，Broker 后续重试；包括损坏 JSON、不支持版本和路由契约不匹配。
     * 它们通常无法靠重试自愈，但有限重试后进入 DLQ 可保留故障证据而非静默丢弃。</p>
     */
    public ConsumeResult handle(MessageView messageView,
                                String consumerName,
                                Map<String, String> routeContract,
                                MessageBusinessHandler handler) {
        try {
            RocketMessageEnvelope<JsonNode> envelope = codec.decode(messageView);
            validateRouteContract(messageView, envelope, routeContract);
            handler.handle(envelope);
            log.info("RocketMQ消费成功: consumer={}, brokerMessageId={}, businessMessageId={}, deliveryAttempt={}",
                    consumerName, messageView.getMessageId(), envelope.getMessageId(), messageView.getDeliveryAttempt());
            return ConsumeResult.SUCCESS;
        } catch (Exception exception) {
            log.error("RocketMQ消费失败，交由Broker重试或DLQ: consumer={}, brokerMessageId={}, deliveryAttempt={}",
                    consumerName,
                    messageView == null ? null : messageView.getMessageId(),
                    messageView == null ? null : messageView.getDeliveryAttempt(),
                    exception);
            return ConsumeResult.FAILURE;
        }
    }

    /**
     * 将“订阅表达式命中了某 Tag”与“该 Tag 的 JSON 事件语义正确”分开校验。
     *
     * <p>允许表是每个 listener 显式传入的窄契约，不是根据 eventType 动态选处理器。
     * 缺失 Tag、未允许 Tag 或 eventType 不一致都是协议失败，抛异常后由公共模板返回 FAILURE。</p>
     */
    private void validateRouteContract(MessageView messageView,
                                       RocketMessageEnvelope<?> envelope,
                                       Map<String, String> routeContract) {
        if (routeContract == null || routeContract.isEmpty()) {
            throw new IllegalArgumentException("消费者Tag/eventType路由契约不能为空");
        }
        String actualTag = messageView.getTag().orElse(null);
        String expectedEventType = actualTag == null ? null : routeContract.get(actualTag);
        if (expectedEventType == null || !expectedEventType.equals(envelope.getEventType())) {
            throw new MessageDecodingException(
                    "消息Tag/eventType路由契约不匹配: actualTag=" + actualTag
                            + ", actualEventType=" + envelope.getEventType()
                            + ", allowed=" + routeContract);
        }
    }

    @FunctionalInterface
    public interface MessageBusinessHandler {
        void handle(RocketMessageEnvelope<JsonNode> envelope) throws Exception;
    }
}
