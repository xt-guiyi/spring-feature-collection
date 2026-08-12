package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * HTTP/application 层批量请求。
 *
 * <p>该模型不承诺 gRPC Client 将整个列表编码成一次 Broker 批量帧；服务会逐项生成消息并返回逐项结果，
 * 这样不同客户端版本的批量 API 差异不会改变学习案例的可观察语义。</p>
 */
@Data
public class RocketBatchMessageRequest {

    @Valid
    @NotEmpty(message = "items不能为空")
    @Size(max = 50, message = "items最多50条")
    private List<RocketBatchMessageItemRequest> items;
}
