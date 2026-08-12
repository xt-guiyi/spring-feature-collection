package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.message.TransactionOrderCommandPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.TransactionOrderItemPayload;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessagePublisher;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketTransactionOrderVO;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.common.Pair;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** RocketMQ 事务半消息入口，与 Transactional Outbox 下单入口完全独立。 */
@Service
@RequiredArgsConstructor
public class RocketTransactionMessageService {

    private final RocketMessageCodec messageCodec;
    private final RocketMessagePublisher publisher;
    private final RocketTransactionRecordService recordService;
    private final RocketTransactionLocalService localService;
    private final MqOrderBusinessMapper orderBusinessMapper;

    /**
     * 实现步骤：
     * 第1步，生成事务 ID 并独立持久化 PREPARED；
     * 第2步，发送暂不可见的事务半消息；
     * 第3步，通过另一个 Spring Bean 执行订单事务并原子标记 COMMITTED；
     * 第4步，本地成功则提交半消息；
     * 第5步，本地调用抛异常时先用持久状态区分“明确回滚”和“数据库已提交但提交结果回包丢失”；
     * 第6步，若数据库已提交但 Broker commit 调用异常，保留 COMMITTED 并等待回查。
     */
    public RocketTransactionOrderVO createTransactionOrder(CompleteOrderCreateRequest request) {
        String transactionId = UUID.randomUUID().toString();
        String businessKey = request.getOrderNo();
        TransactionOrderCommandPayload command = buildCommand(transactionId, request);

        // 第1步：PREPARED 在独立事务中先提交；它只是回查锚点，不代表订单已经成功。
        recordService.prepare(transactionId, businessKey, command);

        Pair<SendReceipt, Transaction> half;
        try {
            // 第2步：编码和半消息发送属于同一准备阶段。任一步失败都要终结刚才独立提交的 PREPARED，
            // 否则数据库会留下一个 Broker 从未见过、也永远不会主动回查的孤儿事务记录。
            RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                    RocketMqNames.EVENT_TRANSACTION_ORDER_CREATED, transactionId, command);
            half = publisher.beginTransaction(RocketMqNames.TRANSACTION_TOPIC, RocketMqNames.TAG_ORDER_CREATED,
                    businessKey, transactionId, envelope);
        } catch (RuntimeException sendFailure) {
            try {
                recordService.markRolledBack(transactionId, "事务消息编码或半消息发送失败: " + concise(sendFailure));
            } catch (RuntimeException stateFailure) {
                // 保留原始发送异常，同时记录为什么 PREPARED 未能收口；不能用第二个异常覆盖第一现场。
                sendFailure.addSuppressed(stateFailure);
            }
            throw sendFailure;
        }

        CompleteOrderResponse order;
        try {
            // 第3步：跨 Bean 调用确保 @Transactional 被代理；订单事实和 COMMITTED 状态同事务提交。
            order = localService.createOrderAndCommitRecord(transactionId, businessKey, command);
        } catch (RuntimeException localFailure) {
            /*
             * 第5步：Spring 抛出事务异常不等于数据库一定回滚。典型反例是 PostgreSQL 已执行 COMMIT，
             * 但提交响应因连接中断没有回到应用；此时订单和事务记录其实都已是 COMMITTED。
             *
             * markRolledBack 的 PREPARED 条件更新就是裁决点：
             * 1. 更新成功：证明本地订单事务没有提交，才能安全 rollback 半消息；
             * 2. 更新 0 行：不能猜测，必须重读 COMMITTED/ROLLED_BACK；
             * 3. 数据库查询本身失败：保持半消息未决，让 Broker 之后回查，绝不贸然 rollback。
             */
            boolean rollbackWon;
            try {
                rollbackWon = recordService.markRolledBack(transactionId, concise(localFailure));
            } catch (RuntimeException stateFailure) {
                localFailure.addSuppressed(stateFailure);
                throw localFailure;
            }
            if (rollbackWon) {
                rollbackHalfMessage(half.getTransaction(), localFailure);
                throw localFailure;
            }

            MqTransactionRecord latest;
            try {
                latest = recordService.getById(transactionId);
            } catch (RuntimeException queryFailure) {
                localFailure.addSuppressed(queryFailure);
                throw localFailure;
            }
            if (latest != null && "ROLLED_BACK".equals(latest.getStatus())) {
                rollbackHalfMessage(half.getTransaction(), localFailure);
                throw localFailure;
            }
            if (latest != null && "COMMITTED".equals(latest.getStatus())) {
                // 数据库事实已经提交，不能再让半消息回滚。即使 commit RPC 不明确，回查也会读到 COMMITTED。
                try {
                    half.getTransaction().commit();
                    return recoveredResult(transactionId, businessKey, latest, half.getSendReceipt(), "COMMITTED",
                            "本地提交调用返回异常，但持久记录显示订单已提交；半消息已按数据库事实提交");
                } catch (Exception commitFailure) {
                    localFailure.addSuppressed(commitFailure);
                    return recoveredResult(transactionId, businessKey, latest, half.getSendReceipt(), "UNKNOWN",
                            "数据库已提交，半消息提交结果不明确，等待Broker依据COMMITTED记录回查: "
                                    + concise(commitFailure));
                }
            }

            // 记录缺失或仍为 PREPARED 时没有足够事实决定提交/回滚。抛回原异常，但把半消息留给 Broker 回查。
            localFailure.addSuppressed(new IllegalStateException(
                    "本地事务结果不明确，未主动回滚半消息；当前持久状态="
                            + (latest == null ? "MISSING" : latest.getStatus())));
            throw localFailure;
        }

        try {
            half.getTransaction().commit();
            return result(transactionId, businessKey, order, half.getSendReceipt(), "COMMITTED", "本地事务与半消息均已提交");
        } catch (Exception commitFailure) {
            // 第6步：Transaction.commit 抛异常也可能已到达 Broker，绝不能撤销已提交订单或改回 ROLLED_BACK。
            return result(transactionId, businessKey, order, half.getSendReceipt(), "UNKNOWN",
                    "数据库已提交，提交半消息结果不明确，等待Broker依据COMMITTED记录回查: " + concise(commitFailure));
        }
    }

    /**
     * 数据库提交结果曾经不明确时，HTTP 栈里的原返回对象可能没有交给调用者；这里重新读取订单事实组装响应。
     * 如果摘要读取也失败，仍返回已知的 transactionId/orderId，消息可靠性不能被一个只读展示查询反向破坏。
     */
    private RocketTransactionOrderVO recoveredResult(String transactionId,
                                                      String businessKey,
                                                      MqTransactionRecord record,
                                                      SendReceipt receipt,
                                                      String state,
                                                      String message) {
        CompleteOrderResponse recoveredOrder = null;
        String resultMessage = message;
        try {
            PgOrder persistedOrder = orderBusinessMapper.selectOrderById(record.getOrderId());
            if (persistedOrder != null) {
                OrderEventPayload payload = RocketOrderApplicationService.buildPayload(
                        persistedOrder, orderBusinessMapper.selectOrderProducts(record.getOrderId()));
                recoveredOrder = new CompleteOrderResponse();
                recoveredOrder.setOrderId(payload.getOrderId());
                recoveredOrder.setOrderNo(payload.getOrderNo());
                recoveredOrder.setTotalAmount(payload.getTotalAmount());
                recoveredOrder.setItemCount(payload.getItemCount());
            } else {
                resultMessage += "；但按orderId重新读取订单摘要时未找到记录";
            }
        } catch (RuntimeException recoveryFailure) {
            resultMessage += "；订单摘要重新读取失败: " + concise(recoveryFailure);
        }

        RocketTransactionOrderVO result = result(
                transactionId, businessKey, recoveredOrder, receipt, state, resultMessage);
        if (recoveredOrder == null) {
            result.setOrderId(record.getOrderId());
            result.setOrderNo(businessKey);
        }
        return result;
    }

    /** 只有数据库已确认 ROLLED_BACK 时才调用；RPC 失败作为原业务异常的 suppressed 证据保留。 */
    private void rollbackHalfMessage(Transaction transaction, RuntimeException localFailure) {
        try {
            transaction.rollback();
        } catch (Exception rollbackFailure) {
            localFailure.addSuppressed(rollbackFailure);
        }
    }

    private TransactionOrderCommandPayload buildCommand(String transactionId, CompleteOrderCreateRequest request) {
        TransactionOrderCommandPayload command = new TransactionOrderCommandPayload();
        command.setTransactionId(transactionId);
        command.setUserId(request.getUserId());
        command.setItems(request.getItems().stream().map(item -> {
            TransactionOrderItemPayload target = new TransactionOrderItemPayload();
            target.setProductId(item.getProductId());
            target.setQuantity(item.getQuantity());
            return target;
        }).toList());
        return command;
    }

    private RocketTransactionOrderVO result(String transactionId,
                                            String businessKey,
                                            CompleteOrderResponse order,
                                            SendReceipt receipt,
                                            String state,
                                            String message) {
        RocketTransactionOrderVO result = new RocketTransactionOrderVO();
        result.setMechanism("ROCKETMQ_TRANSACTION_MESSAGE");
        result.setTransactionId(transactionId);
        result.setBusinessKey(businessKey);
        if (order != null) {
            result.setOrderId(order.getOrderId());
            result.setOrderNo(order.getOrderNo());
            result.setTotalAmount(order.getTotalAmount());
            result.setItemCount(order.getItemCount());
        }
        result.setBrokerMessageId(receipt == null || receipt.getMessageId() == null
                ? null : receipt.getMessageId().toString());
        result.setTransactionState(state);
        result.setMessage(message);
        return result;
    }

    private String concise(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
