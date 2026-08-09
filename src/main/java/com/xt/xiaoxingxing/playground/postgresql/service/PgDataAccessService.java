package com.xt.xiaoxingxing.playground.postgresql.service;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.OrderQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.OrderStatusUpdateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.ProductQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.UserQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.UserStatusUpdateRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgIdCard;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProduct;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
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
import com.xt.xiaoxingxing.shared.common.PageResult;

import java.util.List;

/**
 * PostgreSQL 学习案例的统一业务契约。
 *
 * <p>普通 MyBatis 与 MyBatis-Plus 两套 Service 同时实现本接口，因此 Controller
 * 可以保证接口后缀、参数、返回值和异常语义完全一致。阅读实现时可以直接比较：
 * MyBatis 如何把计算交给数据库，以及 MyBatis-Plus 如何使用官方单表能力配合
 * Java 集合完成等价业务结果。</p>
 */
public interface PgDataAccessService {

    // ==================== 五张表完整 CRUD ====================

    Long createUser(PgUser user);

    PgUser getUser(Long id);

    List<PgUser> listUsers();

    boolean updateUser(PgUser user);

    boolean deleteUser(Long id);

    Long createIdCard(PgIdCard idCard);

    PgIdCard getIdCard(Long id);

    List<PgIdCard> listIdCards();

    boolean updateIdCard(PgIdCard idCard);

    boolean deleteIdCard(Long id);

    Long createOrder(PgOrder order);

    PgOrder getOrder(Long id);

    List<PgOrder> listOrders();

    boolean updateOrder(PgOrder order);

    boolean deleteOrder(Long id);

    Long createProduct(PgProduct product);

    PgProduct getProduct(Long id);

    List<PgProduct> listProducts();

    boolean updateProduct(PgProduct product);

    boolean deleteProduct(Long id);

    Long createOrderProduct(PgOrderProduct orderProduct);

    PgOrderProduct getOrderProduct(Long id);

    List<PgOrderProduct> listOrderProducts();

    boolean updateOrderProduct(PgOrderProduct orderProduct);

    boolean deleteOrderProduct(Long id);

    // ==================== 单表条件、分页与批量操作 ====================

    List<PgUser> searchUsers(UserQueryRequest request);

    PageResult<PgUser> pageUsers(int pageNum, int pageSize);

    boolean batchCreateUsers(List<PgUser> users);

    boolean batchUpdateUserStatus(UserStatusUpdateRequest request);

    boolean batchDeleteUsers(List<Long> ids);

    List<PgOrder> searchOrders(OrderQueryRequest request);

    PageResult<PgOrder> pageOrders(int pageNum, int pageSize);

    boolean updateOrderStatusByCondition(OrderStatusUpdateRequest request);

    List<PgProduct> searchProducts(ProductQueryRequest request);

    // ==================== 六种 JOIN 语义 ====================

    /** INNER JOIN：只返回用户、订单、明细、商品四边均存在的订单详情。 */
    List<OrderDetailVO> getInnerOrderDetails(String orderNo);

    /** LEFT JOIN：保留所有用户，身份证字段允许为空。 */
    List<UserWithIdCardVO> getLeftUsersWithIdCard();

    /** RIGHT JOIN：保留所有用户，没有订单时订单字段为空。 */
    List<UserOrderVO> getRightUsersWithOrders();

    /** FULL OUTER JOIN：商品和订单明细任一侧缺失时仍保留审计记录。 */
    List<ProductOrderAuditVO> getFullProductOrderAudit();

    /** CROSS JOIN：生成受 limit 限制的用户与商品候选组合。 */
    List<UserProductCandidateVO> getCrossUserProductCandidates(int limit);

    /** LATERAL JOIN：每个用户最多返回其最新一笔订单。 */
    List<UserOrderVO> getLatestOrderPerUser();

    // ==================== 聚合、子查询、函数与高级 SQL ====================

    List<UserOrderStatVO> getUserOrderStats();

    List<UserOrderStatVO> getTopSpendingUsers(int limit);

    List<UserSimpleVO> getUsersWithOrders();

    List<UserSimpleVO> getUsersWithoutOrders();

    List<UserOrderStatVO> getUsersByOrderCount(int minOrderCount);

    List<UserSimpleVO> getUsersUnionAll();

    List<OrderStatusVO> getOrderStatusWithName();

    /**
     * 查询订单状态枚举映射结果。
     *
     * <p>数据库状态码先自动转换为 Java 枚举，再由 Service 将枚举 code/text 平铺为
     * status/statusName，便于和 CASE WHEN、Java switch 两种实现直接比较。</p>
     */
    List<OrderStatusVO> getOrderStatusWithEnumMapping();

    List<UserContactVO> getUsersWithCoalescePhone();

    List<UserDateStatVO> getUserDateStats();

    List<UserRankVO> getUserSpendingRank();

    List<UserSpendingLevelVO> getUserSpendingLevels();

    List<ProductSalesStatVO> getProductSalesStats();

    // ==================== 跨表事务 ====================

    /**
     * 在同一个 playground 数据源事务中创建订单、写入明细并扣减库存。
     * 任一步骤失败都必须回滚，不能留下只有订单头或部分明细的数据。
     */
    CompleteOrderResponse createCompleteOrder(CompleteOrderCreateRequest request);
}
