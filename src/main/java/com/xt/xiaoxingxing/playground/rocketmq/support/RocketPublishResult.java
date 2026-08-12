package com.xt.xiaoxingxing.playground.rocketmq.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.rocketmq.client.apis.producer.SendReceipt;

/**
 * 面向业务层的发布结果，隔离 RocketMQ Client 的 SendReceipt 类型。
 *
 * <p>businessMessageId 在发送前生成，重试同一业务事件时必须保持稳定；brokerMessageId 是 Broker 对某次
 * 实际投递的标识，多次发布尝试可能不同，不能用它代替业务幂等键。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocketPublishResult {

    private boolean success;
    private String businessMessageId;
    private String brokerMessageId;
    private String topic;
    private String tag;
    private String messageKey;
    private String reason;

    public static RocketPublishResult success(String businessMessageId,
                                              SendReceipt receipt,
                                              String topic,
                                              String tag,
                                              String messageKey) {
        String brokerMessageId = receipt == null || receipt.getMessageId() == null
                ? null : receipt.getMessageId().toString();
        return new RocketPublishResult(true, businessMessageId, brokerMessageId, topic, tag, messageKey,
                "Broker已接收消息");
    }

    /** 异步发送仅表示客户端已受理；真正的 Broker 结果在回调日志中体现。 */
    public static RocketPublishResult accepted(String businessMessageId,
                                               String topic,
                                               String tag,
                                               String messageKey) {
        return new RocketPublishResult(true, businessMessageId, null, topic, tag, messageKey,
                "客户端已受理异步发送，等待Broker结果");
    }

    public static RocketPublishResult failure(String businessMessageId,
                                              String topic,
                                              String tag,
                                              String messageKey,
                                              String reason) {
        return new RocketPublishResult(false, businessMessageId, null, topic, tag, messageKey, reason);
    }
}
