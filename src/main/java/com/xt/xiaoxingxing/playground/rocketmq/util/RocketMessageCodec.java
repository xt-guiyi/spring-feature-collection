package com.xt.xiaoxingxing.playground.rocketmq.util;

import com.xt.xiaoxingxing.shared.exception.BusinessException;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** RocketMQ 消息解析工具。 */
@Component
public class RocketMessageCodec {

    /** 解析消息中的事务 ID。 */
    public String decodeTransactionId(MessageView messageView) {
        ByteBuffer buffer = messageView.getBody();
        byte[] body = new byte[buffer.remaining()];
        buffer.get(body);
        String transactionId = new String(body, StandardCharsets.UTF_8);
        if (transactionId.isBlank()) {
            throw new BusinessException("RocketMQ消息缺少transactionId");
        }
        return transactionId;
    }
}
