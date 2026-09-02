package com.xt.xiaoxingxing.playground.features.postgresql.mapper;

import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.OrderQueryRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.OrderStatusUpdateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * orders 表的普通 MyBatis CRUD、动态查询和分页 Mapper。
 *
 * <p>返回 int 的写方法表示受影响行数，Service 通过它区分“更新成功”和“目标不存在”；
 * insertOrder 则借助 PostgreSQL RETURNING 直接返回新主键。</p>
 */
@Mapper
public interface PgMyBatisOrderMapper {

    /** 插入订单头并返回数据库生成的 ID。 */
    Long insertOrder(PgOrder order);

    /** 按主键读取单行。 */
    PgOrder selectOrderById(@Param("id") Long id);

    /** 查询全部订单，并由 XML 提供确定性排序。 */
    List<PgOrder> selectAllOrders();

    /** 使用动态 set 选择性更新非空字段。 */
    int updateOrder(PgOrder order);

    /** 按主键删除并返回受影响行数。 */
    int deleteOrderById(@Param("id") Long id);

    /** 组合用户、状态与金额范围条件。 */
    List<PgOrder> selectOrdersByCondition(OrderQueryRequest request);

    /** 分页前单独统计总行数。 */
    long countOrders();

    /** 使用 LIMIT/OFFSET 查询当前页。 */
    List<PgOrder> selectOrderPage(@Param("offset") long offset, @Param("pageSize") int pageSize);

    /** 按动态条件批量修改状态，Service 负责禁止无条件全表更新。 */
    int updateOrderStatusByCondition(OrderStatusUpdateRequest request);
}
