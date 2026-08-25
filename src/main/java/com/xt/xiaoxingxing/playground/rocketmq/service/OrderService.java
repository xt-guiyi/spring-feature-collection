package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.xt.xiaoxingxing.playground.rocketmq.config.OrderMqConstants;
import com.xt.xiaoxingxing.playground.rocketmq.dto.CreateOrderItemRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.CreateOrderRequest;
import com.xt.xiaoxingxing.playground.rocketmq.dto.OrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Order;
import com.xt.xiaoxingxing.playground.rocketmq.entity.OrderItem;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Product;
import com.xt.xiaoxingxing.playground.rocketmq.enums.MqTransactionStatus;
import com.xt.xiaoxingxing.playground.rocketmq.enums.OrderOperation;
import com.xt.xiaoxingxing.playground.rocketmq.enums.OrderStatus;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.OrderMapper;
import com.xt.xiaoxingxing.playground.rocketmq.repository.TransactionRecordRepository;
import com.xt.xiaoxingxing.playground.rocketmq.util.RocketMqUtil;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/** 订单服务。 */
@Slf4j
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final TransactionRecordRepository transactionRecordRepository;
    private final RocketMqUtil rocketMqUtil;
    private final TransactionTemplate transactionTemplate;

    public OrderService(OrderMapper orderMapper,
                        TransactionRecordRepository transactionRecordRepository,
                        RocketMqUtil rocketMqUtil,
                        @Qualifier("playgroundTransactionManager")
                        PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.transactionRecordRepository = transactionRecordRepository;
        this.rocketMqUtil = rocketMqUtil;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 创建订单。 */
    public OrderResponse createOrder(CreateOrderRequest request) {

        String transactionId = UUID.randomUUID().toString();
        return executeTransaction(
                transactionId,
                OrderOperation.CREATE,
                request.getOrderNo(),
                () -> createOrderLocally(request));
    }

    /** 支付订单。 */
    public OrderResponse payOrder(Long orderId) {
        Order current = BusinessAssert.notNull(orderMapper.selectOrderById(orderId), "事务订单不存在");
        String orderNo = current.getOrderNo();

        String transactionId = UUID.randomUUID().toString();
        return executeTransaction(
                transactionId,
                OrderOperation.PAY,
                orderNo,
                () -> payOrderLocally(orderId));
    }

    /** 取消超时未支付的订单。 */
    public void cancelExpiredOrder(String orderNo) {
        Order current = BusinessAssert.notNull(orderMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
        if (!OrderStatus.PENDING.name().equals(current.getStatus())) {
            log.info("订单无需超时取消: orderNo={}, status={}", orderNo, current.getStatus());
            return;
        }

        String transactionId = UUID.randomUUID().toString();
        try {
            executeTransaction(
                    transactionId,
                    OrderOperation.CANCEL,
                    orderNo,
                    () -> cancelOrderLocally(current.getId()));
        } catch (BusinessException exception) {
            Order latest = BusinessAssert.notNull(
                    orderMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
            if (!OrderStatus.PENDING.name().equals(latest.getStatus())) {
                log.info("订单无需超时取消: orderNo={}, status={}", orderNo, latest.getStatus());
                return;
            }
            throw exception;
        }
    }

    /** 执行订单操作并发送事务消息。 */
    private OrderResponse executeTransaction(String transactionId,
                                             OrderOperation operation,
                                             String orderNo,
                                             Supplier<OrderResponse> localAction) {
        transactionRecordRepository.prepare(transactionId);

        Transaction half;
        try {
            half = rocketMqUtil.sendTransaction(
                    OrderMqConstants.TOPIC_TRANSACTION, operation.tag(), orderNo, transactionId)
                    .getTransaction();
        } catch (RuntimeException sendFailure) {
            markRolledBackAfterSendFailure(transactionId, sendFailure);
            throw sendFailure;
        }

        OrderResponse response;
        try {
            response = transactionTemplate.execute(status -> {
                OrderResponse localResponse = localAction.get();
                BusinessAssert.isTrue(
                        transactionRecordRepository.markCommitted(transactionId),
                        "事务记录已不再是PREPARED，拒绝提交重复或过期的" + operation.name() + "操作");
                return localResponse;
            });
        } catch (RuntimeException localFailure) {
            return resolveLocalFailure(transactionId, operation, orderNo, half, localFailure);
        }

        commitHalfOrWarn(half, transactionId, operation);
        return response;
    }



    /** 消息发送失败后将事务记录标记为已回滚。 */
    private void markRolledBackAfterSendFailure(String transactionId, RuntimeException sendFailure) {
        try {
            transactionRecordRepository.markRolledBack(
                    transactionId, "半消息发送失败: " + concise(sendFailure));
        } catch (RuntimeException stateFailure) {
            sendFailure.addSuppressed(stateFailure);
        }
    }

    /** 处理订单本地操作失败。 */
    private OrderResponse resolveLocalFailure(String transactionId,
                                              OrderOperation operation,
                                              String orderNo,
                                              Transaction half,
                                              RuntimeException localFailure) {
        MqTransactionRecord current;
        try {
            current = transactionRecordRepository.findById(transactionId);
        } catch (RuntimeException queryFailure) {
            localFailure.addSuppressed(queryFailure);
            throw localFailure;
        }

        if (current != null && current.getStatus() == MqTransactionStatus.COMMITTED) {
            Order order = BusinessAssert.notNull(
                    orderMapper.selectOrderByOrderNo(orderNo), "已提交事务对应订单不存在");
            OrderResponse persisted = OrderResponse.from(
                    order, orderMapper.selectOrderItems(order.getId()));
            commitHalfOrWarn(half, transactionId, operation);
            return persisted;
        }

        if (current != null && current.getStatus() == MqTransactionStatus.PREPARED) {
            try {
                transactionRecordRepository.markRolledBack(transactionId, concise(localFailure));
                current = transactionRecordRepository.findById(transactionId);
            } catch (RuntimeException stateFailure) {
                localFailure.addSuppressed(stateFailure);
                throw localFailure;
            }
        }

        if (current != null && current.getStatus() == MqTransactionStatus.COMMITTED) {
            Order order = BusinessAssert.notNull(
                    orderMapper.selectOrderByOrderNo(orderNo), "已提交事务对应订单不存在");
            OrderResponse persisted = OrderResponse.from(
                    order, orderMapper.selectOrderItems(order.getId()));
            commitHalfOrWarn(half, transactionId, operation);
            return persisted;
        }
        if (current != null && current.getStatus() == MqTransactionStatus.ROLLED_BACK) {
            rollbackHalfMessage(half, localFailure);
        }
        throw localFailure;
    }

    /** 创建订单并扣减库存。 */
    private OrderResponse createOrderLocally(CreateOrderRequest request) {
        TreeMap<Long, Integer> quantities = new TreeMap<>();
        for (CreateOrderItemRequest item : request.getItems()) {
            int currentQuantity = quantities.getOrDefault(item.getProductId(), 0);
            long mergedQuantity = (long) currentQuantity + item.getQuantity();
            BusinessAssert.isTrue(mergedQuantity <= Integer.MAX_VALUE, "同一商品购买数量过大");
            quantities.put(item.getProductId(), (int) mergedQuantity);
        }

        BusinessAssert.isTrue(orderMapper.existsUser(request.getUserId()), "下单用户不存在");
        List<Product> products = orderMapper.selectProductsByIds(new ArrayList<>(quantities.keySet()));
        Map<Long, Product> productsById = new TreeMap<>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
        }
        BusinessAssert.isTrue(productsById.size() == quantities.size(), "订单中包含不存在的商品");

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productsById.get(entry.getKey());
            totalAmount = totalAmount.add(
                    product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setOrderNo(request.getOrderNo());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING.name());
        order.setCreatedAt(LocalDateTime.now());
        try {
            order.setId(orderMapper.insertOrder(order));
        } catch (DuplicateKeyException duplicateKeyException) {
            BusinessException businessException = new BusinessException("订单号已存在");
            businessException.addSuppressed(duplicateKeyException);
            throw businessException;
        }

        List<OrderItem> items = new ArrayList<>(quantities.size());
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productsById.get(entry.getKey());
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(entry.getKey());
            item.setQuantity(entry.getValue());
            item.setUnitPrice(product.getPrice());
            items.add(item);
        }
        orderMapper.batchInsertOrderItems(items);
        for (OrderItem item : items) {
            BusinessAssert.isTrue(orderMapper.decrementStock(item.getProductId(), item.getQuantity()) == 1,
                    "商品库存已发生变化，请重试");
        }
        return OrderResponse.from(order, items);
    }

    /** 更新订单为已支付。 */
    private OrderResponse payOrderLocally(Long orderId) {
        if (orderMapper.markPaid(orderId) != 1) {
            throw new BusinessException("支付失败：订单不是PENDING");
        }
        Order order = BusinessAssert.notNull(orderMapper.selectOrderById(orderId), "事务订单不存在");
        return OrderResponse.from(order, orderMapper.selectOrderItems(orderId));
    }

    /** 取消订单并恢复库存。 */
    private OrderResponse cancelOrderLocally(Long orderId) {
        if (orderMapper.markCancelled(orderId) != 1) {
            throw new BusinessException("超时取消失败：订单不是PENDING");
        }
        List<OrderItem> items = orderMapper.selectOrderItems(orderId);
        for (OrderItem item : items) {
            orderMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
        Order cancelled = BusinessAssert.notNull(orderMapper.selectOrderById(orderId), "事务订单不存在");
        return OrderResponse.from(cancelled, items);
    }


    /** 提交半消息。 */
    private void commitHalfOrWarn(Transaction half, String transactionId, OrderOperation operation) {
        try {
            half.commit();
        } catch (Exception commitFailure) {
            log.warn("数据库已提交但半消息commit结果不明确，等待Broker回查: transactionId={}, operationType={}",
                    transactionId, operation.name(), commitFailure);
        }
    }

    /** 回滚半消息。 */
    private void rollbackHalfMessage(Transaction half, RuntimeException originalFailure) {
        try {
            half.rollback();
        } catch (Exception rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    /** 获取异常摘要。 */
    private String concise(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

}
