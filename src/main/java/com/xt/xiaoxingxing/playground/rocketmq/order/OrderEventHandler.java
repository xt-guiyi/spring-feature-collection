package com.xt.xiaoxingxing.playground.rocketmq.order;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.product.ProductService;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 两套可靠消息订单共同使用的业务副作用处理器。
 *
 * <p>Outbox 与 RocketMQ 事务消息拥有不同的 Listener 和 ConsumerGroup，但“删除商品缓存”和
 * “记录订单统计”是相同业务能力，所以统一收口在这里。调用方只传消费者名、稳定 messageId、事件类型和
 * 已解码的订单负载；本类不知道 MessageView、Topic、Tag、消息信封，也不根据消息机制写 mode 分支。</p>
 */
@Service
public class OrderEventHandler {

    private final MqConsumerRecordMapper consumerRecordMapper;
    private final ProductService productService;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate writeTransactionTemplate;

    public OrderEventHandler(MqConsumerRecordMapper consumerRecordMapper,
                             ProductService productService,
                             @Qualifier("playgroundTransactionManager")
                             PlatformTransactionManager transactionManager) {
        this.consumerRecordMapper = consumerRecordMapper;
        this.productService = productService;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.readTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.writeTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 删除库存发生变化的商品缓存。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>短只读事务检查 {@code (consumerName,messageId)} 是否已经处理；</li>
     *     <li>尚未处理时，在 PostgreSQL 事务之外删除 Redis 缓存；</li>
     *     <li>Redis 删除成功后，再用短事务插入消费幂等记录；</li>
     *     <li>并发重复投递由数据库唯一约束裁决，插入 0 行按幂等成功结束。</li>
     * </ol>
     *
     * <p>Redis 与 PostgreSQL 无法参加一个本地事务，因此绝不能先提交“已消费”记录再删除缓存：若随后
     * Redis 失败，Broker 重投会被幂等记录挡住，陈旧缓存将永久保留。当前顺序若在删除后、记录前崩溃，
     * 重投只会再次删除相同键；删除缓存天然幂等，这个失败窗口是安全的。</p>
     */
    public void invalidateProductCache(String consumerName,
                                       String messageId,
                                       String eventType,
                                       OrderEventPayload payload) {
        // 第1步：缓存只受扣库存和恢复库存影响，支付事件不能误触发本方法。
        validateCommonInput(consumerName, messageId, eventType, payload);
        BusinessAssert.isTrue(
                RocketMqNames.EVENT_ORDER_CREATED.equals(eventType)
                        || RocketMqNames.EVENT_ORDER_CANCELLED.equals(eventType),
                "商品缓存失效只接受ORDER_CREATED或ORDER_CANCELLED");
        BusinessAssert.isTrue(payload.getProductIds() != null && !payload.getProductIds().isEmpty()
                        && payload.getProductIds().stream().allMatch(id -> id != null && id > 0),
                "商品缓存失效事件缺少合法productIds");

        Boolean alreadyConsumed = readTransactionTemplate.execute(status ->
                consumerRecordMapper.existsConsumed(consumerName, messageId));
        if (Boolean.TRUE.equals(alreadyConsumed)) {
            return;
        }

        /*
         * 第2步：这里故意位于两个 TransactionTemplate 之间。ProductService 不吞 Redis 删除异常，
         * 异常会一直抛到 Listener，RocketConsumerSupport 返回 FAILURE 后由 Broker 重投。
         */
        productService.evictProducts(payload.getProductIds());

        // 第3步：删除成功后登记幂等记录。若另一个实例也完成了相同删除，唯一约束让其中一个插入 0 行；
        // 两次 Redis delete 都是安全的，不需要把并发重复当作消费失败。
        writeTransactionTemplate.executeWithoutResult(status ->
                consumerRecordMapper.insertConsumedIfAbsent(
                        newConsumedMessage(consumerName, messageId, eventType, payload.getOrderId())));
    }

    /**
     * 原子记录订单统计投影。
     *
     * <p>消费幂等 INSERT 和统计 UPSERT 必须位于同一个 PostgreSQL 事务：若统计失败，幂等记录同步回滚，
     * Broker 重投后仍能重新处理；若消息重复，唯一约束使 INSERT 返回 0，本事务直接结束且不再次累计。</p>
     */
    public void recordStatistics(String consumerName,
                                 String messageId,
                                 String eventType,
                                 OrderEventPayload payload) {
        // 第1步：统计只接受订单创建、支付和取消三种已经发生的业务事实。
        validateCommonInput(consumerName, messageId, eventType, payload);
        BusinessAssert.isTrue(
                RocketMqNames.EVENT_ORDER_CREATED.equals(eventType)
                        || RocketMqNames.EVENT_ORDER_PAID.equals(eventType)
                        || RocketMqNames.EVENT_ORDER_CANCELLED.equals(eventType),
                "订单统计只接受ORDER_CREATED、ORDER_PAID或ORDER_CANCELLED");
        BusinessAssert.isTrue(payload.getTotalAmount() != null && payload.getTotalAmount().signum() >= 0,
                "订单统计事件缺少合法totalAmount");

        writeTransactionTemplate.executeWithoutResult(status -> {
            // 第2步：先竞争消费组+messageId 唯一键；0 行表示同一消费组已经成功处理过。
            MqConsumedMessage consumed = newConsumedMessage(
                    consumerName, messageId, eventType, payload.getOrderId());
            if (consumerRecordMapper.insertConsumedIfAbsent(consumed) != 1) {
                return;
            }

            // 第3步：统计 UPSERT 与上面的幂等 INSERT 同事务；异常会让两条 SQL 一起回滚。
            BusinessAssert.isTrue(consumerRecordMapper.upsertStatistics(
                            eventType, payload.getTotalAmount(), consumed.getConsumedAt()) == 1,
                    "订单统计更新失败");
        });
    }

    private void validateCommonInput(String consumerName,
                                     String messageId,
                                     String eventType,
                                     OrderEventPayload payload) {
        BusinessAssert.isTrue(consumerName != null && !consumerName.isBlank(), "consumerName不能为空");
        BusinessAssert.isTrue(messageId != null && !messageId.isBlank(), "messageId不能为空");
        BusinessAssert.isTrue(eventType != null && !eventType.isBlank(), "eventType不能为空");
        BusinessAssert.notNull(payload, "订单事件payload不能为空");
        BusinessAssert.isTrue(payload.getOrderId() != null && payload.getOrderId() > 0,
                "订单事件缺少合法orderId");
    }

    private MqConsumedMessage newConsumedMessage(String consumerName,
                                                 String messageId,
                                                 String eventType,
                                                 Long orderId) {
        MqConsumedMessage consumed = new MqConsumedMessage();
        consumed.setConsumerName(consumerName);
        consumed.setMessageId(messageId);
        consumed.setEventType(eventType);
        consumed.setAggregateId(String.valueOf(orderId));
        consumed.setConsumedAt(LocalDateTime.now());
        return consumed;
    }
}
