package com.xt.xiaoxingxing.playground.rabbitmq.support;

/** JSON 已损坏或缺少必要字段，这类消息重试也不会自动变好，应最终进入死信队列。 */
public class MessageDecodingException extends RuntimeException {

    public MessageDecodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageDecodingException(String message) {
        super(message);
    }
}
