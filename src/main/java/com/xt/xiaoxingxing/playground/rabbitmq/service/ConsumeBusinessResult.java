package com.xt.xiaoxingxing.playground.rabbitmq.service;

/** 业务处理结果用于日志区分；三种结果都已经安全完成，因此监听器都应 ACK。 */
public enum ConsumeBusinessResult {
    PROCESSED,
    DUPLICATE,
    STALE
}
