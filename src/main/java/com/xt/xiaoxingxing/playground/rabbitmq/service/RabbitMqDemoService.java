package com.xt.xiaoxingxing.playground.rabbitmq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqLearningProperties;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitAckDemoRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitOrderingDemoRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitRoutingMessageRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.dto.request.RabbitStreamEventRequest;
import com.xt.xiaoxingxing.playground.rabbitmq.message.DemoMessagePayload;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitMessageCodec;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitPublishResult;
import com.xt.xiaoxingxing.playground.rabbitmq.support.ReliableRabbitPublisher;
import com.xt.xiaoxingxing.playground.rabbitmq.vo.RabbitMessagePublishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Direct、Topic、Fanout、Return、确认行为和顺序消费的生产者学习入口。 */
@Service
@RequiredArgsConstructor
public class RabbitMqDemoService {

    private final RabbitMessageCodec messageCodec;
    private final ReliableRabbitPublisher publisher;
    private final RabbitStreamTemplate rabbitStreamTemplate;
    private final RabbitMqLearningProperties properties;

    public RabbitMessagePublishVO sendDirect(RabbitRoutingMessageRequest request) {
        return publishText(RabbitMqNames.LEARNING_DIRECT_EXCHANGE, request.getRoutingKey(), request.getMessage());
    }

    public RabbitMessagePublishVO sendTopic(RabbitRoutingMessageRequest request) {
        return publishText(RabbitMqNames.LEARNING_TOPIC_EXCHANGE, request.getRoutingKey(), request.getMessage());
    }

    public RabbitMessagePublishVO sendFanout(String message) {
        // Fanout Exchange 不检查 routingKey，传空字符串能更直观地表达“广播与路由键无关”。
        return publishText(RabbitMqNames.LEARNING_FANOUT_EXCHANGE, "", message);
    }

    public RabbitMessagePublishVO sendMandatoryReturn(String message) {
        /*
         * 这个 Routing Key 故意没有任何 Binding。
         * Broker 可能先接收消息，但 mandatory=true 会将它 Return 给生产者，结果 success=false。
         */
        return publishText(RabbitMqNames.LEARNING_DIRECT_EXCHANGE, "demo.no.binding", message);
    }

    public RabbitMessagePublishVO sendAckDemo(RabbitAckDemoRequest request) {
        DemoMessagePayload payload = new DemoMessagePayload();
        payload.setText(request.getMessage());
        payload.setAction(request.getAction().name());
        payload.setFailTimes(request.getFailTimes());

        RabbitMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                RabbitMqNames.EVENT_DEMO_MESSAGE, UUID.randomUUID().toString(), payload);
        Message message = MessageBuilder.fromClonedMessage(messageCodec.encode(envelope))
                .setHeader(RabbitMqNames.HEADER_DEMO_ACTION, request.getAction().name())
                .setHeader(RabbitMqNames.HEADER_DEMO_FAIL_TIMES, request.getFailTimes())
                .setHeader(RabbitMqNames.HEADER_RETRY_COUNT, 0)
                .build();
        RabbitPublishResult result = publisher.publishMessageAndWait(
                RabbitMqNames.LEARNING_DIRECT_EXCHANGE,
                RabbitMqNames.ACK_DEMO_KEY,
                message,
                envelope.getMessageId());
        return RabbitMessagePublishVO.from(result);
    }

    public List<RabbitMessagePublishVO> sendOrdered(RabbitOrderingDemoRequest request) {
        List<RabbitMessagePublishVO> results = new ArrayList<>();
        for (int sequence = 1; sequence <= request.getCount(); sequence++) {
            DemoMessagePayload payload = new DemoMessagePayload();
            payload.setText("顺序消息-" + sequence);
            payload.setBusinessKey(request.getBusinessKey());
            payload.setSequence(sequence);

            RabbitMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                    RabbitMqNames.EVENT_DEMO_MESSAGE, request.getBusinessKey(), payload);
            results.add(RabbitMessagePublishVO.from(publisher.publishAndWait(
                    RabbitMqNames.LEARNING_DIRECT_EXCHANGE,
                    RabbitMqNames.ORDERING_DEMO_KEY,
                    envelope)));
        }
        return results;
    }

    /** 使用原生 Stream 协议直接追加一条可回放事件。 */
    public RabbitMessagePublishVO sendStreamEvent(RabbitStreamEventRequest request) {
        RabbitMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                request.getEventType(), request.getAggregateId(), request.getPayload());
        try {
            byte[] json = messageCodec.toJson(envelope).getBytes(StandardCharsets.UTF_8);
            boolean confirmed = rabbitStreamTemplate.send(
                            rabbitStreamTemplate.messageBuilder().addData(json).build())
                    .get(properties.getConfirmTimeoutSeconds(), TimeUnit.SECONDS);

            RabbitPublishResult result = confirmed
                    ? RabbitPublishResult.success(envelope.getMessageId(),
                            "native-stream", RabbitMqNames.ORDER_AUDIT_STREAM)
                    : RabbitPublishResult.failure(envelope.getMessageId(),
                            "native-stream", RabbitMqNames.ORDER_AUDIT_STREAM, "Stream Broker未确认消息");
            return RabbitMessagePublishVO.from(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RabbitMessagePublishVO.from(RabbitPublishResult.failure(
                    envelope.getMessageId(), "native-stream", RabbitMqNames.ORDER_AUDIT_STREAM,
                    "等待Stream发布确认时线程被中断"));
        } catch (Exception e) {
            return RabbitMessagePublishVO.from(RabbitPublishResult.failure(
                    envelope.getMessageId(), "native-stream", RabbitMqNames.ORDER_AUDIT_STREAM,
                    "Stream发布失败: " + e.getMessage()));
        }
    }

    private RabbitMessagePublishVO publishText(String exchange, String routingKey, String text) {
        DemoMessagePayload payload = new DemoMessagePayload();
        payload.setText(text);
        RabbitMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                RabbitMqNames.EVENT_DEMO_MESSAGE, UUID.randomUUID().toString(), payload);
        return RabbitMessagePublishVO.from(publisher.publishAndWait(exchange, routingKey, envelope));
    }
}
