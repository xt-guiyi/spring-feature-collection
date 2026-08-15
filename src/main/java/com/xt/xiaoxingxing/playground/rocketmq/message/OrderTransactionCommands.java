package com.xt.xiaoxingxing.playground.rocketmq.message;

import java.util.List;

/**
 * RocketMQ 事务半消息使用的最小订单命令集合。
 *
 * <p>CREATE 与 PAY/CANCEL 需要的输入不同，拆开后每种命令都不存在“只有某个操作才使用”的空字段。
 * 这些 record 是跨进程消息协议的一部分；新增字段时需要同步考虑 {@code schemaVersion} 的兼容策略。</p>
 */
public final class OrderTransactionCommands {

    private OrderTransactionCommands() {
    }

    /**
     * 创建订单命令。单价和总金额不能由客户端消息决定，本地事务仍应根据 PostgreSQL 商品事实重新计算。
     *
     * <p>紧凑构造器把明细复制为不可变列表，防止半消息编码前调用方继续修改原集合，导致调用方刚完成校验的
     * 命令与最终编码正文之间出现难以复现的差异。PREPARED记录只保存事务裁决所需的业务键和状态，不复制消息正文。</p>
     */
    public record CreateOrderCommand(String orderNo, Long userId, List<OrderItem> items) {

        public CreateOrderCommand {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** PAY 和 CANCEL 只需要目标订单 ID，操作语义由事务记录的 operationType 和消息事件类型共同确定。 */
    public record OrderIdCommand(Long orderId) {
    }

    /** 创建订单命令中的一条商品输入，只保存商品 ID 和购买数量。 */
    public record OrderItem(Long productId, Integer quantity) {
    }
}
