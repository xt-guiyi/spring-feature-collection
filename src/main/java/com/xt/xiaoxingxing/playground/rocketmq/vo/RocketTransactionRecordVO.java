package com.xt.xiaoxingxing.playground.rocketmq.vo;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import lombok.Data;

import java.time.LocalDateTime;

/** RocketMQ 半消息事务的运维响应；不把事务表实体直接作为 HTTP 契约。 */
@Data
public class RocketTransactionRecordVO {

    private String transactionId;
    private String businessKey;
    /** 持久化的本地事务命令快照，仅用于学习观察和故障核对。 */
    private String requestPayload;
    private String status;
    private Long orderId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RocketTransactionRecordVO from(MqTransactionRecord source) {
        if (source == null) {
            return null;
        }
        RocketTransactionRecordVO target = new RocketTransactionRecordVO();
        target.setTransactionId(source.getTransactionId());
        target.setBusinessKey(source.getBusinessKey());
        target.setRequestPayload(source.getRequestPayload());
        target.setStatus(source.getStatus());
        target.setOrderId(source.getOrderId());
        target.setLastError(source.getLastError());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
