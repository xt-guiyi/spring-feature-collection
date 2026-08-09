package com.xt.xiaoxingxing.playground.rabbitmq.service;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderItemRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.service.PgMyBatisService;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rabbitmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rabbitmq.vo.RabbitOrderCreateVO;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** 可靠消息版本的订单应用服务：复用原下单事务，并在同一事务追加 Outbox。 */
@Service
@RequiredArgsConstructor
public class RabbitOrderApplicationService {

    private final PgMyBatisService pgMyBatisService;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final OutboxEventService outboxEventService;

    /**
     * 创建完整订单并写入两个 Outbox 事件。
     *
     * <p>完整步骤：</p>
     * <ol>
     *     <li>复用现有普通 MyBatis 完整下单，创建订单、明细并原子扣减库存；</li>
     *     <li>构造不信任客户端价格的订单事件负载；</li>
     *     <li>追加立即发布的 ORDER_CREATED；</li>
     *     <li>追加先进入 30 分钟延迟队列的超时检查事件；</li>
     *     <li>方法正常返回后由同一个 PostgreSQL 事务一次提交全部数据。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public RabbitOrderCreateVO createOrder(CompleteOrderCreateRequest request) {
        // 第1步：该方法本身已有事务，因此内部 createCompleteOrder 会加入同一个事务，不会提前提交。
        CompleteOrderResponse order = pgMyBatisService.createCompleteOrder(request);

        // 第2步：金额使用 createCompleteOrder 根据数据库商品价格计算的结果；不从请求接收单价。
        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getOrderId());
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(request.getUserId());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setItemCount(order.getItemCount());
        payload.setProductIds(request.getItems().stream()
                .map(CompleteOrderItemRequest::getProductId)
                .distinct()
                .sorted()
                .toList());

        // 第3步：订单创建事件发布到 Topic Exchange，会分别进入缓存、统计、通知和 Stream。
        String createdMessageId = outboxEventService.append(
                String.valueOf(order.getOrderId()),
                RabbitMqNames.EVENT_ORDER_CREATED,
                RabbitMqNames.ORDER_EVENT_EXCHANGE,
                RabbitMqNames.ORDER_CREATED_KEY,
                payload);

        // 第4步：超时检查不直接发往消费者，而是先进入 TTL=30分钟的延迟队列。
        String timeoutMessageId = outboxEventService.append(
                String.valueOf(order.getOrderId()),
                RabbitMqNames.EVENT_ORDER_PAYMENT_TIMEOUT_CHECK,
                RabbitMqNames.ORDER_DELAY_EXCHANGE,
                RabbitMqNames.ORDER_TIMEOUT_DELAY_KEY,
                payload);

        // 第5步：返回 Outbox messageId 只是方便学习观察；RabbitMQ 实际发送由定时器异步完成。
        RabbitOrderCreateVO result = new RabbitOrderCreateVO();
        result.setOrderId(order.getOrderId());
        result.setOrderNo(order.getOrderNo());
        result.setTotalAmount(order.getTotalAmount());
        result.setItemCount(order.getItemCount());
        result.setOrderCreatedMessageId(createdMessageId);
        result.setPaymentTimeoutMessageId(timeoutMessageId);
        return result;
    }

    /**
     * 使用条件更新支付订单，并在同一事务追加 ORDER_PAID。
     *
     * <p>支付和超时取消都只允许从 PENDING 转换。两个事务并发时，PostgreSQL 行锁会让它们依次检查条件，
     * 最终只能有一个 UPDATE 影响一行，从而避免“既支付又取消”。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean payOrder(Long orderId) {
        /*
         * 完整步骤：
         * 第1步：读取订单，给不存在的订单明确提示；
         * 第2步：执行 WHERE status='PENDING' 的条件更新；
         * 第3步：读取订单明细，构造支付事件；
         * 第4步：写入 ORDER_PAID Outbox；
         * 第5步：事务统一提交。
         */
        PgOrder order = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "订单不存在");

        int affected = orderBusinessMapper.markPaid(orderId);
        BusinessAssert.isTrue(affected == 1,
                "只有PENDING订单可以支付，当前状态: " + order.getStatus());

        List<PgOrderProduct> items = orderBusinessMapper.selectOrderProducts(orderId);
        OrderEventPayload payload = buildPayload(order, items);

        outboxEventService.append(
                String.valueOf(orderId),
                RabbitMqNames.EVENT_ORDER_PAID,
                RabbitMqNames.ORDER_EVENT_EXCHANGE,
                RabbitMqNames.ORDER_PAID_KEY,
                payload);
        return true;
    }

    private OrderEventPayload buildPayload(PgOrder order, List<PgOrderProduct> items) {
        List<PgOrderProduct> sortedItems = items.stream()
                .sorted(Comparator.comparing(PgOrderProduct::getProductId))
                .toList();

        long itemCount = sortedItems.stream()
                .map(PgOrderProduct::getQuantity)
                .mapToLong(Integer::longValue)
                .sum();
        BusinessAssert.isTrue(itemCount <= Integer.MAX_VALUE, "订单商品总数过大");

        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getId());
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(order.getUserId());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setItemCount((int) itemCount);
        payload.setProductIds(sortedItems.stream().map(PgOrderProduct::getProductId).toList());
        return payload;
    }
}
