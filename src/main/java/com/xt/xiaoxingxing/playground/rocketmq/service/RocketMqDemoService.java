package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketBatchMessageItemRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketBatchMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketDelayMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketFifoMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketRetryMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.request.RocketTextMessageRequest;
import com.xt.xiaoxingxing.playground.rocketmq.message.DemoMessagePayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessagePublisher;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketMessagePublishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 普通、异步、Tag、多组、FIFO、延迟、重试和应用层批量的生产端学习服务。 */
@Service
@RequiredArgsConstructor
public class RocketMqDemoService {

    private final RocketMessageCodec messageCodec;
    private final RocketMessagePublisher publisher;

    public RocketMessagePublishVO sendNormal(RocketTextMessageRequest request) {
        return publishNormal(request.getText(), request.getTag(), false);
    }

    /** HTTP 立即返回 accepted；这时 Broker 结果只能稍后从异步回调日志观察。 */
    public RocketMessagePublishVO sendAsync(RocketTextMessageRequest request) {
        return publishNormal(request.getText(), request.getTag(), true);
    }

    /** Tag 是 Topic 内二级过滤条件；使用 DEMO 可被示例监听器匹配。 */
    public RocketMessagePublishVO sendTag(RocketTextMessageRequest request) {
        return publishNormal(request.getText(), request.getTag(), false);
    }

    /** 同一条消息由两个不同 ConsumerGroup 各消费一份，同组多实例则共同分担。 */
    public RocketMessagePublishVO sendMultiGroup(RocketTextMessageRequest request) {
        return publishNormal(request.getText(), RocketMqNames.TAG_DEMO, false);
    }

    /**
     * 实现步骤：第1步固定 businessKey 为 MessageGroup；第2步依次创建序号 1..count；
     * 第3步逐条同步发送并保留每条结果。顺序只在这个 MessageGroup 内成立，不是 Topic 全局顺序。
     */
    public List<RocketMessagePublishVO> sendFifo(RocketFifoMessageRequest request) {
        List<RocketMessagePublishVO> results = new ArrayList<>();
        // 第1步：相同 businessKey 作为 MessageGroup，让同一业务聚合进入同一 FIFO 序列。
        String group = request.getBusinessKey();
        // 第2步：sequence 是业务观察字段，不是 Broker 的全局序号。
        for (int sequence = 1; sequence <= request.getCount(); sequence++) {
            DemoMessagePayload payload = payload("FIFO消息-" + sequence, group, null, sequence);
            RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                    RocketMqNames.EVENT_DEMO_MESSAGE, group, payload);
            // 第3步：任一条失败都保留独立结果；调用者能看出序列在哪一次发布中断。
            results.add(RocketMessagePublishVO.from(publisher.publishFifo(
                    RocketMqNames.FIFO_TOPIC, RocketMqNames.TAG_DEMO, group, group, envelope)));
        }
        return results;
    }

    public RocketMessagePublishVO sendDelay(RocketDelayMessageRequest request) {
        long expectedAt = Instant.now().plusSeconds(request.getDelaySeconds()).toEpochMilli();
        // businessKey 在这个学习案例中保存预期投递毫秒时间，消费者据此打印实际漂移。
        DemoMessagePayload payload = payload(request.getText(), String.valueOf(expectedAt), null, null);
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                RocketMqNames.EVENT_DEMO_MESSAGE, UUID.randomUUID().toString(), payload);
        return RocketMessagePublishVO.from(publisher.publishDelay(
                RocketMqNames.DELAY_TOPIC, RocketMqNames.TAG_DEMO, envelope.getMessageId(),
                Duration.ofSeconds(request.getDelaySeconds()), envelope));
    }

    public RocketMessagePublishVO sendRetry(RocketRetryMessageRequest request) {
        DemoMessagePayload payload = payload(request.getText(), UUID.randomUUID().toString(),
                request.getFailTimes(), null);
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                RocketMqNames.EVENT_DEMO_MESSAGE, payload.getBusinessKey(), payload);
        return RocketMessagePublishVO.from(publisher.publishNormal(
                RocketMqNames.NORMAL_TOPIC, RocketMqNames.TAG_RETRY, envelope.getMessageId(), envelope));
    }

    /**
     * 应用层批量：每项生成独立信封和发布结果。这里不宣称所有 gRPC Client 版本都会把列表变成
     * 一次 Broker 批量帧；逐项结果反而能清晰展示部分成功和失败。
     */
    public List<RocketMessagePublishVO> sendBatch(RocketBatchMessageRequest request) {
        List<RocketMessagePublishVO> results = new ArrayList<>();
        // 第1步：逐项创建稳定业务 ID，某一项失败不抹掉其他项结果。
        for (RocketBatchMessageItemRequest item : request.getItems()) {
            RocketTextMessageRequest single = new RocketTextMessageRequest();
            single.setText(item.getText());
            single.setTag(item.getTag());
            // 第2步：调用普通同步发布以获得每项 Broker messageId。
            results.add(sendNormal(single));
        }
        return results;
    }

    private RocketMessagePublishVO publishNormal(String text, String tag, boolean async) {
        String businessKey = UUID.randomUUID().toString();
        DemoMessagePayload payload = payload(text, businessKey, null, null);
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                RocketMqNames.EVENT_DEMO_MESSAGE, businessKey, payload);
        return RocketMessagePublishVO.from(async
                ? publisher.publishNormalAsync(RocketMqNames.NORMAL_TOPIC, tag, businessKey, envelope)
                : publisher.publishNormal(RocketMqNames.NORMAL_TOPIC, tag, businessKey, envelope));
    }

    private DemoMessagePayload payload(String text, String businessKey, Integer failTimes, Integer sequence) {
        DemoMessagePayload payload = new DemoMessagePayload();
        payload.setText(text);
        payload.setBusinessKey(businessKey);
        payload.setFailTimes(failTimes);
        payload.setSequence(sequence);
        return payload;
    }
}
