package com.xt.xiaoxingxing.playground.rocketmq.message;

import lombok.Data;

/** 基础、重试、延迟和 FIFO 演示共用的最小业务负载。 */
@Data
public class DemoMessagePayload {

    private String text;
    /** 业务检索键；FIFO 演示中同时作为 MessageGroup。 */
    private String businessKey;
    /** 需要故意失败多少次，用于观察 Broker 重试和最终 DLQ。 */
    private Integer failTimes;
    /** 同一 businessKey 内的业务序号。 */
    private Integer sequence;
}
