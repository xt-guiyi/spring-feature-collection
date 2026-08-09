package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderStatusEnumDemo;
import com.xt.xiaoxingxing.playground.postgresql.vo.OrderDetailVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.OrderStatusVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.ProductOrderAuditVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.ProductSalesStatVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserContactVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserDateStatVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserOrderStatVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserOrderVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserProductCandidateVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserRankVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserSimpleVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserSpendingLevelVO;
import com.xt.xiaoxingxing.playground.postgresql.vo.UserWithIdCardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 普通 MyBatis 复杂查询入口。
 *
 * <p>这里的方法有意保持“一种 SQL 知识点一个方法”，对应 XML 会详细解释
 * 连接方向、空值语义和数据库执行的聚合阶段。</p>
 */
@Mapper
public interface PgMyBatisQueryMapper {

    /** INNER JOIN：仅返回订单、用户、明细、商品四表都匹配的数据。 */
    List<OrderDetailVO> selectInnerOrderDetails(@Param("orderNo") String orderNo);

    /** LEFT JOIN：保留全部用户，身份证字段允许为空。 */
    List<UserWithIdCardVO> selectLeftUsersWithIdCard();

    /** RIGHT JOIN：保留右侧全部用户，订单字段允许为空。 */
    List<UserOrderVO> selectRightUsersWithOrders();

    /** FULL OUTER JOIN：同时审计未售商品和失去商品引用的明细。 */
    List<ProductOrderAuditVO> selectFullProductOrderAudit();

    /** CROSS JOIN：生成受 limit 保护的用户商品笛卡尔积。 */
    List<UserProductCandidateVO> selectCrossUserProductCandidates(@Param("limit") int limit);

    /** LEFT JOIN LATERAL：为每个用户取最新一笔订单。 */
    List<UserOrderVO> selectLatestOrderPerUser();

    /** GROUP BY：统计用户订单数和消费金额。 */
    List<UserOrderStatVO> selectUserOrderStats();

    /** 聚合子查询：取消费金额最高的前 N 名用户。 */
    List<UserOrderStatVO> selectTopSpendingUsers(@Param("limit") int limit);

    /** EXISTS：筛选至少存在一笔订单的用户。 */
    List<UserSimpleVO> selectUsersWithOrders();

    /** NOT EXISTS：筛选完全没有订单的用户。 */
    List<UserSimpleVO> selectUsersWithoutOrders();

    /** HAVING：聚合后保留订单数达到下界的用户。 */
    List<UserOrderStatVO> selectUsersByOrderCount(@Param("minOrderCount") int minOrderCount);

    /** UNION ALL：不去重地拼接两组互斥用户。 */
    List<UserSimpleVO> selectUsersUnionAll();

    /** CASE WHEN：把订单状态码转换为可读名称。 */
    List<OrderStatusVO> selectOrderStatusWithName();

    /**
     * 枚举自动映射：SQL 只返回原始状态码，由当前 SqlSessionFactory 中注册的
     * MyBatis-Plus 枚举类型处理器把 status 转成枚举。
     *
     * <p>这个方法故意不写 CASE WHEN，也不返回 status_name，用于与
     * {@link #selectOrderStatusWithName()} 的数据库文本转换方式进行对照。</p>
     */
    List<PgOrderStatusEnumDemo> selectOrderStatusWithEnumMapping();

    /** COALESCE：为缺失手机号提供默认文本。 */
    List<UserContactVO> selectUsersWithCoalescePhone();

    /** PostgreSQL 日期转换与 EXTRACT 年份提取。 */
    List<UserDateStatVO> selectUserDateStats();

    /** ROW_NUMBER：按消费金额生成连续排名。 */
    List<UserRankVO> selectUserSpendingRank();

    /** CTE：先统计消费，再根据金额划分等级。 */
    List<UserSpendingLevelVO> selectUserSpendingLevels();

    /** 商品维度聚合：统计销量、销售额和当前库存。 */
    List<ProductSalesStatVO> selectProductSalesStats();
}
