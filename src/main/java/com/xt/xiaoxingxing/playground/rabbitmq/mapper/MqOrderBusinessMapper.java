package com.xt.xiaoxingxing.playground.rabbitmq.mapper;

import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** RabbitMQ 支付与超时消费者需要的最小订单 SQL 集合。 */
@Mapper
public interface MqOrderBusinessMapper {

    PgOrder selectOrderById(@Param("orderId") Long orderId);

    int markPaid(@Param("orderId") Long orderId);

    int markCancelled(@Param("orderId") Long orderId);

    List<PgOrderProduct> selectOrderProducts(@Param("orderId") Long orderId);

    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
