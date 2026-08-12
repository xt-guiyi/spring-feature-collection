package com.xt.xiaoxingxing.playground.rocketmq.service;

/** 消费业务的安全终态；三种结果都可以向 Broker 返回 SUCCESS。 */
public enum ConsumeBusinessResult {
    PROCESSED,
    DUPLICATE,
    STALE
}
