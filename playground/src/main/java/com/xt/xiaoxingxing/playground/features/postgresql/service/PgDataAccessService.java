package com.xt.xiaoxingxing.playground.features.postgresql.service;

import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.*;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.response.*;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgIdCard;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgProduct;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.*;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import org.springframework.beans.BeanUtils;

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

    // HTTP 直接使用 Request/Response DTO；Entity 和查询 VO 只留在 Service 内部。
    default Long createUser(PgUserRequest source) { return createUser(copy(source, new PgUser())); }
    default PgUserResponse getUserResponse(Long id) { return copy(getUser(id), new PgUserResponse()); }
    default List<PgUserResponse> listUsersResponse() { return listUsers().stream().map(source -> copy(source, new PgUserResponse())).toList(); }
    default boolean updateUser(PgUserRequest source) { return updateUser(copy(source, new PgUser())); }
    default Long createIdCard(PgIdCardRequest source) { return createIdCard(copy(source, new PgIdCard())); }
    default PgIdCardResponse getIdCardResponse(Long id) { return copy(getIdCard(id), new PgIdCardResponse()); }
    default List<PgIdCardResponse> listIdCardsResponse() { return listIdCards().stream().map(source -> copy(source, new PgIdCardResponse())).toList(); }
    default boolean updateIdCard(PgIdCardRequest source) { return updateIdCard(copy(source, new PgIdCard())); }
    default Long createOrder(PgOrderRequest source) { return createOrder(copy(source, new PgOrder())); }
    default PgOrderResponse getOrderResponse(Long id) { return copy(getOrder(id), new PgOrderResponse()); }
    default List<PgOrderResponse> listOrdersResponse() { return listOrders().stream().map(source -> copy(source, new PgOrderResponse())).toList(); }
    default boolean updateOrder(PgOrderRequest source) { return updateOrder(copy(source, new PgOrder())); }
    default Long createProduct(PgProductRequest source) { return createProduct(copy(source, new PgProduct())); }
    default PgProductResponse getProductResponse(Long id) { return copy(getProduct(id), new PgProductResponse()); }
    default List<PgProductResponse> listProductsResponse() { return listProducts().stream().map(source -> copy(source, new PgProductResponse())).toList(); }
    default boolean updateProduct(PgProductRequest source) { return updateProduct(copy(source, new PgProduct())); }
    default Long createOrderProduct(PgOrderProductRequest source) { return createOrderProduct(copy(source, new PgOrderProduct())); }
    default PgOrderProductResponse getOrderProductResponse(Long id) { return copy(getOrderProduct(id), new PgOrderProductResponse()); }
    default List<PgOrderProductResponse> listOrderProductsResponse() { return listOrderProducts().stream().map(source -> copy(source, new PgOrderProductResponse())).toList(); }
    default boolean updateOrderProduct(PgOrderProductRequest source) { return updateOrderProduct(copy(source, new PgOrderProduct())); }
    default List<PgUserResponse> searchUsersResponse(UserQueryRequest source) {
        return searchUsers(source).stream()
                .map(row -> copy(row, new PgUserResponse())).toList();
    }
    default PageResult<PgUserResponse> pageUsersResponse(int pageNum, int pageSize) {
        return toPage(pageUsers(pageNum, pageSize), row -> copy(row, new PgUserResponse()));
    }
    default boolean batchCreateUsersResponse(List<PgUserRequest> source) {
        return batchCreateUsers(source.stream().map(row -> copy(row, new PgUser())).toList());
    }
    default List<PgOrderResponse> searchOrdersResponse(OrderQueryRequest source) {
        return searchOrders(source).stream()
                .map(row -> copy(row, new PgOrderResponse())).toList();
    }
    default PageResult<PgOrderResponse> pageOrdersResponse(int pageNum, int pageSize) {
        return toPage(pageOrders(pageNum, pageSize), row -> copy(row, new PgOrderResponse()));
    }
    default List<PgProductResponse> searchProductsResponse(ProductQueryRequest source) {
        return searchProducts(source).stream()
                .map(row -> copy(row, new PgProductResponse())).toList();
    }
    default List<OrderDetailResponse> getInnerOrderDetailsResponse(String orderNo) {
        return getInnerOrderDetails(orderNo).stream().map(row -> copy(row, new OrderDetailResponse())).toList();
    }
    default List<UserWithIdCardResponse> getLeftUsersWithIdCardResponse() {
        return getLeftUsersWithIdCard().stream().map(row -> copy(row, new UserWithIdCardResponse())).toList();
    }
    default List<UserOrderResponse> getRightUsersWithOrdersResponse() {
        return getRightUsersWithOrders().stream().map(row -> copy(row, new UserOrderResponse())).toList();
    }
    default List<ProductOrderAuditResponse> getFullProductOrderAuditResponse() {
        return getFullProductOrderAudit().stream().map(row -> copy(row, new ProductOrderAuditResponse())).toList();
    }
    default List<UserProductCandidateResponse> getCrossUserProductCandidatesResponse(int limit) {
        return getCrossUserProductCandidates(limit).stream().map(row -> copy(row, new UserProductCandidateResponse())).toList();
    }
    default List<UserOrderResponse> getLatestOrderPerUserResponse() {
        return getLatestOrderPerUser().stream().map(row -> copy(row, new UserOrderResponse())).toList();
    }
    default List<UserOrderStatResponse> getUserOrderStatsResponse() { return getUserOrderStats().stream().map(row -> copy(row, new UserOrderStatResponse())).toList(); }
    default List<UserOrderStatResponse> getTopSpendingUsersResponse(int limit) { return getTopSpendingUsers(limit).stream().map(row -> copy(row, new UserOrderStatResponse())).toList(); }
    default List<UserSimpleResponse> getUsersWithOrdersResponse() { return getUsersWithOrders().stream().map(row -> copy(row, new UserSimpleResponse())).toList(); }
    default List<UserSimpleResponse> getUsersWithoutOrdersResponse() { return getUsersWithoutOrders().stream().map(row -> copy(row, new UserSimpleResponse())).toList(); }
    default List<UserOrderStatResponse> getUsersByOrderCountResponse(int minOrderCount) { return getUsersByOrderCount(minOrderCount).stream().map(row -> copy(row, new UserOrderStatResponse())).toList(); }
    default List<UserSimpleResponse> getUsersUnionAllResponse() { return getUsersUnionAll().stream().map(row -> copy(row, new UserSimpleResponse())).toList(); }
    default List<OrderStatusResponse> getOrderStatusWithNameResponse() { return getOrderStatusWithName().stream().map(row -> copy(row, new OrderStatusResponse())).toList(); }
    default List<OrderStatusResponse> getOrderStatusWithEnumMappingResponse() { return getOrderStatusWithEnumMapping().stream().map(row -> copy(row, new OrderStatusResponse())).toList(); }
    default List<UserContactResponse> getUsersWithCoalescePhoneResponse() { return getUsersWithCoalescePhone().stream().map(row -> copy(row, new UserContactResponse())).toList(); }
    default List<UserDateStatResponse> getUserDateStatsResponse() { return getUserDateStats().stream().map(row -> copy(row, new UserDateStatResponse())).toList(); }
    default List<UserRankResponse> getUserSpendingRankResponse() { return getUserSpendingRank().stream().map(row -> copy(row, new UserRankResponse())).toList(); }
    default List<UserSpendingLevelResponse> getUserSpendingLevelsResponse() { return getUserSpendingLevels().stream().map(row -> copy(row, new UserSpendingLevelResponse())).toList(); }
    default List<ProductSalesStatResponse> getProductSalesStatsResponse() { return getProductSalesStats().stream().map(row -> copy(row, new ProductSalesStatResponse())).toList(); }
    default CompleteOrderResponse createCompleteOrderResponse(CompleteOrderCreateRequest source) {
        return createCompleteOrder(source);
    }

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

    private static <S, T> PageResult<T> toPage(PageResult<S> source,
                                                java.util.function.Function<S, T> mapper) {
        PageResult<T> target = new PageResult<>();
        target.setList(source.getList().stream().map(mapper).toList());
        target.setTotal(source.getTotal());
        target.setPageNum(source.getPageNum());
        target.setPageSize(source.getPageSize());
        return target;
    }

    private static <S, T> T copy(S source, T target) {
        if (source != null) {
            BeanUtils.copyProperties(source, target);
        }
        return target;
    }
}
