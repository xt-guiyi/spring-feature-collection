package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RocketMQ 支付、超时和取消消费者所需的最小订单 SQL 集合。
 *
 * <p>条件更新的返回值是并发竞争结果：1 表示本事务赢得状态转换，0 则表示订单已被其他事务
 * 支付、取消或不存在；0 行不得继续恢复库存。</p>
 */
@Mapper
public interface MqOrderBusinessMapper {

    PgOrder selectOrderById(@Param("orderId") Long orderId);

    /** 按订单稳定业务键查询；事务记录、消息 aggregateId 和 RocketMQ Key 都统一使用 orderNo。 */
    PgOrder selectOrderByOrderNo(@Param("orderNo") String orderNo);

    /** Cache Aside 未命中或 Redis 故障时，从 PostgreSQL 读取权威商品库存。 */
    PgProduct selectProductById(@Param("productId") Long productId);

    int markPaid(@Param("orderId") Long orderId);

    int markCancelled(@Param("orderId") Long orderId);

    /** 按 productId 升序返回，调用方按此顺序恢复库存以降低死锁概率。 */
    List<PgOrderProduct> selectOrderProducts(@Param("orderId") Long orderId);

    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
