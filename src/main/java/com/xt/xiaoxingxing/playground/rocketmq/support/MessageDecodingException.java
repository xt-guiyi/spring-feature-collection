package com.xt.xiaoxingxing.playground.rocketmq.support;

/** 消息 JSON、字符编码、必填字段或 Tag/eventType 路由契约无法正确解析时抛出。 */
public class MessageDecodingException extends RuntimeException {

    public MessageDecodingException(String message) {
        super(message);
    }

    public MessageDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
