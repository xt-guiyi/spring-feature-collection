package com.xt.xiaoxingxing.playground.features.rocketmq.entity;

import com.xt.xiaoxingxing.playground.features.rocketmq.enums.MqTransactionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 事务消息记录。 */
@Data
public class MqTransactionRecord {

    private String transactionId;
    private MqTransactionStatus status;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
