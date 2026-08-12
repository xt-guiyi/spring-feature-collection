package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderItemRequest;
import com.xt.xiaoxingxing.playground.postgresql.service.PgMyBatisService;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqTransactionRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.TransactionOrderCommandPayload;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在独立 Spring Bean 中执行事务消息对应的订单本地事务，避免 self-invocation 绕过事务代理。 */
@Service
@RequiredArgsConstructor
public class RocketTransactionLocalService {

    private final PgMyBatisService pgMyBatisService;
    private final MqTransactionRecordMapper transactionRecordMapper;

    /**
     * 实现步骤：
     * 第1步，把持久化命令还原为现有完整下单请求；
     * 第2步，创建订单、明细并按商品 ID 顺序条件扣库存；
     * 第3步，在同一 PostgreSQL 事务把 PREPARED 条件更新为 COMMITTED 并关联 orderId。
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public CompleteOrderResponse createOrderAndCommitRecord(String transactionId,
                                                            String businessKey,
                                                            TransactionOrderCommandPayload command) {
        // 第1步：businessKey 即稳定订单号；事务命令只保存服务端完成下单所需字段。
        CompleteOrderCreateRequest request = new CompleteOrderCreateRequest();
        request.setOrderNo(businessKey);
        request.setUserId(command.getUserId());
        request.setItems(command.getItems().stream().map(item -> {
            CompleteOrderItemRequest target = new CompleteOrderItemRequest();
            target.setProductId(item.getProductId());
            target.setQuantity(item.getQuantity());
            return target;
        }).toList());

        // 第2步：复用既有下单事务；库存判断和扣减由同一条带 stock 条件的 UPDATE 最终裁决并发。
        CompleteOrderResponse order = pgMyBatisService.createCompleteOrder(request);

        // 第3步：条件更新只允许 PREPARED -> COMMITTED；0 行说明记录已被另一终结路径处理，必须回滚订单。
        BusinessAssert.isTrue(transactionRecordMapper.markCommitted(transactionId, order.getOrderId()) == 1,
                "事务记录已不再是PREPARED，拒绝提交重复或过期的本地订单事务");
        return order;
    }
}
