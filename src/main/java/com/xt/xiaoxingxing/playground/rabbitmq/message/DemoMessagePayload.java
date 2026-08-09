package com.xt.xiaoxingxing.playground.rabbitmq.message;

import lombok.Data;

/** 基础路由、失败注入和顺序消费案例使用的简单负载。 */
@Data
public class DemoMessagePayload {

    private String text;
    private String action;
    private Integer failTimes;
    private String businessKey;
    private Integer sequence;
}
