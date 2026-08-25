package com.xt.xiaoxingxing.playground.rocketmq.mapper;

import com.xt.xiaoxingxing.playground.rocketmq.entity.Order;
import com.xt.xiaoxingxing.playground.rocketmq.entity.OrderItem;
import com.xt.xiaoxingxing.playground.rocketmq.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 订单数据访问接口。 */
@Mapper
public interface OrderMapper {

    /** 查询用户是否存在。 */
    boolean existsUser(@Param("userId") Long userId);

    /** 根据商品 ID 列表查询商品。 */
    List<Product> selectProductsByIds(@Param("productIds") List<Long> productIds);

    /** 新增订单。 */
    Long insertOrder(Order order);

    /** 批量新增订单明细。 */
    int batchInsertOrderItems(@Param("items") List<OrderItem> items);

    /** 扣减商品库存。 */
    int decrementStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 根据订单 ID 查询订单。 */
    Order selectOrderById(@Param("orderId") Long orderId);

    /** 根据订单号查询订单。 */
    Order selectOrderByOrderNo(@Param("orderNo") String orderNo);

    /** 根据商品 ID 查询商品。 */
    Product selectProductById(@Param("productId") Long productId);

    /** 将订单标记为已支付。 */
    int markPaid(@Param("orderId") Long orderId);

    /** 将订单标记为已取消。 */
    int markCancelled(@Param("orderId") Long orderId);

    /** 查询订单明细。 */
    List<OrderItem> selectOrderItems(@Param("orderId") Long orderId);

    /** 恢复商品库存。 */
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
