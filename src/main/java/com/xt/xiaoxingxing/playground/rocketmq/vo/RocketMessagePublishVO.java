package com.xt.xiaoxingxing.playground.rocketmq.vo;

import com.xt.xiaoxingxing.playground.rocketmq.support.RocketPublishResult;
import lombok.Data;

/** HTTP 层发布结果，不暴露 RocketMQ Client 的 SendReceipt 等框架对象。 */
@Data
public class RocketMessagePublishVO {

    private boolean success;
    /** true 仅表示异步发送已被客户端受理，brokerMessageId 要在回调完成后才可能出现。 */
    private boolean accepted;
    private String businessMessageId;
    private String brokerMessageId;
    private String topic;
    private String tag;
    private String messageKey;
    private String reason;

    public static RocketMessagePublishVO from(RocketPublishResult result) {
        RocketMessagePublishVO vo = new RocketMessagePublishVO();
        vo.setSuccess(result.isSuccess());
        vo.setAccepted(result.isSuccess() && result.getBrokerMessageId() == null);
        vo.setBusinessMessageId(result.getBusinessMessageId());
        vo.setBrokerMessageId(result.getBrokerMessageId());
        vo.setTopic(result.getTopic());
        vo.setTag(result.getTag());
        vo.setMessageKey(result.getMessageKey());
        vo.setReason(result.getReason());
        return vo;
    }
}
