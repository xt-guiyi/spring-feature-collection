package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderItemRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.service.PgMyBatisService;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketOrderCreateVO;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 订单应用入口：把订单事实与 Outbox 事件放进同一个 PostgreSQL 本地事务。 */
@Service
@RequiredArgsConstructor
public class RocketOrderApplicationService {

    private final PgMyBatisService pgMyBatisService;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final OutboxEventService outboxEventService;
    private final RocketMqLearningProperties properties;

    /**
     * 创建完整订单，同时写入创建事件和付款超时检查事件。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>复用现有 MyBatis 完整下单事务，按商品 ID 顺序原子扣库存并保存订单；</li>
     *     <li>只使用持久化结果和数据库价格构造事件负载；</li>
     *     <li>追加立即发布的 ORDER_CREATED；</li>
     *     <li>追加投递时间为订单超时点的 DELAY 事件；</li>
     *     <li>返回两个稳定业务消息 ID，事务正常返回后统一提交。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public RocketOrderCreateVO createOutboxOrder(CompleteOrderCreateRequest request) {
        // 第1步：createCompleteOrder 加入当前事务；任一 Outbox 插入失败，订单和库存扣减也会回滚。
        CompleteOrderResponse order = pgMyBatisService.createCompleteOrder(request);

        // 第2步：金额来自数据库成交价格，不接受客户端伪造；productIds 排序也便于稳定观察消息内容。
        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getOrderId());
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(request.getUserId());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setItemCount(order.getItemCount());
        payload.setProductIds(request.getItems().stream()
                .map(CompleteOrderItemRequest::getProductId).distinct().sorted().toList());

        // 第3步：不同 ConsumerGroup 会各自收到创建事件，分别处理缓存、统计和通知。
        String createdMessageId = outboxEventService.append(
                String.valueOf(order.getOrderId()), RocketMqNames.EVENT_ORDER_CREATED,
                RocketMqNames.NORMAL_TOPIC, RocketMqNames.TAG_ORDER_CREATED,
                order.getOrderNo(), null, null, payload);

        // 第4步：延迟消息只是未来触发检查，真正能否取消由数据库 PENDING 条件更新裁决。
        LocalDateTime deliverAt = LocalDateTime.now()
                .plus(Duration.ofMillis(properties.getOrderTimeoutMillis()));
        String timeoutMessageId = outboxEventService.append(
                String.valueOf(order.getOrderId()), RocketMqNames.EVENT_ORDER_PAYMENT_TIMEOUT_CHECK,
                RocketMqNames.DELAY_TOPIC, RocketMqNames.TAG_ORDER_TIMEOUT,
                order.getOrderNo(), null, deliverAt, payload);

        // 第5步：返回的是应用业务消息 ID；实际 Broker 投递由 Outbox 调度器异步推进。
        RocketOrderCreateVO result = new RocketOrderCreateVO();
        result.setMechanism("TRANSACTIONAL_OUTBOX");
        result.setOrderId(order.getOrderId());
        result.setOrderNo(order.getOrderNo());
        result.setTotalAmount(order.getTotalAmount());
        result.setItemCount(order.getItemCount());
        result.setOrderCreatedMessageId(createdMessageId);
        result.setPaymentTimeoutMessageId(timeoutMessageId);
        return result;
    }

    /**
     * 支付和超时取消通过同一订单行上的条件更新竞争，只允许一个事务从 PENDING 前进。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>读取订单用于不存在提示；</li>
     *     <li>条件更新 PENDING 到 PAID；</li>
     *     <li>重新读取持久化订单与明细并构造当前事实；</li>
     *     <li>在同一事务追加 ORDER_PAID Outbox。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean payOrder(Long orderId) {
        // 第1步：这次读取只改善错误信息；它不是并发裁决依据。
        PgOrder before = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "订单不存在");

        // 第2步：affected=0 可能表示另一事务已经支付或超时取消，不能仅解释成“订单不存在”。
        int affected = orderBusinessMapper.markPaid(orderId);
        BusinessAssert.isTrue(affected == 1,
                "支付条件更新失败：订单可能已被其他事务支付或取消，读取时状态为 " + before.getStatus());

        // 第3步：读取数据库当前事实。普通并发消息不保证完成顺序，后续消费者也不能让状态倒退。
        PgOrder paid = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "支付后订单不存在");
        List<PgOrderProduct> items = orderBusinessMapper.selectOrderProducts(orderId);
        OrderEventPayload payload = buildPayload(paid, items);

        // 第4步：数据库状态和消息意图一次提交；直接在事务里发 Broker 会重新引入双写窗口。
        outboxEventService.append(String.valueOf(orderId), RocketMqNames.EVENT_ORDER_PAID,
                RocketMqNames.NORMAL_TOPIC, RocketMqNames.TAG_ORDER_PAID,
                paid.getOrderNo(), null, null, payload);
        return true;
    }

    static OrderEventPayload buildPayload(PgOrder order, List<PgOrderProduct> items) {
        List<PgOrderProduct> sorted = items.stream()
                .sorted(Comparator.comparing(PgOrderProduct::getProductId)).toList();
        long count = sorted.stream().map(PgOrderProduct::getQuantity).mapToLong(Integer::longValue).sum();
        BusinessAssert.isTrue(count <= Integer.MAX_VALUE, "订单商品总数过大");
        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getId());
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(order.getUserId());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setItemCount((int) count);
        payload.setProductIds(sorted.stream().map(PgOrderProduct::getProductId).toList());
        return payload;
    }
}
