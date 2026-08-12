package com.xt.xiaoxingxing.playground.rocketmq.support;

/** 消费者收到当前代码无法解释的协议版本时抛出，避免按错误结构执行业务副作用。 */
public class UnsupportedMessageVersionException extends RuntimeException {

    public UnsupportedMessageVersionException(String message) {
        super(message);
    }
}
