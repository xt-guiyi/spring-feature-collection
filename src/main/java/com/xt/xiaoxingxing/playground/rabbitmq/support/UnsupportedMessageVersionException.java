package com.xt.xiaoxingxing.playground.rabbitmq.support;

/** 消费者不认识该 schemaVersion，继续重试没有意义，需要人工升级消费者或转换消息。 */
public class UnsupportedMessageVersionException extends RuntimeException {

    public UnsupportedMessageVersionException(String message) {
        super(message);
    }
}
