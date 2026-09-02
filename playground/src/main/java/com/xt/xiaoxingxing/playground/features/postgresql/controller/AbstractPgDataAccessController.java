package com.xt.xiaoxingxing.playground.features.postgresql.controller;

import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.*;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.response.*;
import com.xt.xiaoxingxing.playground.features.postgresql.service.PgDataAccessService;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 两套 PostgreSQL 学习入口共享的端点定义。
 *
 * <p>普通 MyBatis 和 MyBatis-Plus Controller 继承同一组方法，仅类级 URL 前缀不同。
 * 这样可以从结构上保证所有学习案例一一对应，避免某一侧新增接口后另一侧遗漏。
 * 每个方法只负责 HTTP 参数转换，技术差异全部保留在各自 Service 中。</p>
 */
public abstract class AbstractPgDataAccessController {

    private final PgDataAccessService service;

    protected AbstractPgDataAccessController(PgDataAccessService service) {
        this.service = service;
    }

    // ==================== users 完整 CRUD ====================

    /** 创建用户；可分别观察 XML RETURNING 与 BaseMapper insert 的主键回填方式。 */
    @PostMapping("/users")
    public Result<Long> createUser(@RequestBody PgUserRequest request) {
        return Result.ok(service.createUser(request));
    }

    /** 按主键查询用户；记录不存在时两套实现都抛出相同业务异常。 */
    @GetMapping("/users/{id}")
    public Result<PgUserResponse> getUser(@PathVariable Long id) {
        return Result.ok(service.getUserResponse(id));
    }

    /** 查询全部用户并按 ID 升序返回。 */
    @GetMapping("/users")
    public Result<List<PgUserResponse>> listUsers() {
        return Result.ok(service.listUsersResponse());
    }

    /** 按路径 ID 更新用户，Body 中只需提供需要修改的非空字段。 */
    @PutMapping("/users/{id}")
    public Result<Boolean> updateUser(@PathVariable Long id, @RequestBody PgUserRequest request) {
        request.setId(id);
        return Result.ok(service.updateUser(request));
    }

    /** 按主键删除用户，返回是否实际删除了一行。 */
    @DeleteMapping("/users/{id}")
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        return Result.ok(service.deleteUser(id));
    }

    // ==================== id_cards 完整 CRUD ====================

    /** 创建身份证记录，users 与 id_cards 通过 user_id 表达一对一关系。 */
    @PostMapping("/id-cards")
    public Result<Long> createIdCard(@RequestBody PgIdCardRequest request) {
        return Result.ok(service.createIdCard(request));
    }

    /** 按身份证记录主键查询；这里的主键不是 card_number。 */
    @GetMapping("/id-cards/{id}")
    public Result<PgIdCardResponse> getIdCard(@PathVariable Long id) {
        return Result.ok(service.getIdCardResponse(id));
    }

    /** 查询全部身份证记录，便于与 LEFT JOIN 的“用户保留侧”结果进行对照。 */
    @GetMapping("/id-cards")
    public Result<List<PgIdCardResponse>> listIdCards() {
        return Result.ok(service.listIdCardsResponse());
    }

    /** 局部更新身份证记录；未传字段不会被 XML set 或 updateById 修改。 */
    @PutMapping("/id-cards/{id}")
    public Result<Boolean> updateIdCard(@PathVariable Long id, @RequestBody PgIdCardRequest request) {
        request.setId(id);
        return Result.ok(service.updateIdCard(request));
    }

    /** 删除关联记录本身，不会删除 users 表中的用户。 */
    @DeleteMapping("/id-cards/{id}")
    public Result<Boolean> deleteIdCard(@PathVariable Long id) {
        return Result.ok(service.deleteIdCard(id));
    }

    // ==================== orders 完整 CRUD ====================

    /** 创建单独的订单头；需要同时创建明细和扣库存时使用 /orders/complete。 */
    @PostMapping("/orders")
    public Result<Long> createOrder(@RequestBody PgOrderRequest request) {
        return Result.ok(service.createOrder(request));
    }

    /** 按订单主键查询订单头，订单商品明细需要通过关联表接口或 JOIN 案例查询。 */
    @GetMapping("/orders/{id}")
    public Result<PgOrderResponse> getOrder(@PathVariable Long id) {
        return Result.ok(service.getOrderResponse(id));
    }

    /** 查询全部订单头并保持稳定排序，方便比较两套实现返回值。 */
    @GetMapping("/orders")
    public Result<List<PgOrderResponse>> listOrders() {
        return Result.ok(service.listOrdersResponse());
    }

    /** 局部更新订单；金额若提供必须大于等于零。 */
    @PutMapping("/orders/{id}")
    public Result<Boolean> updateOrder(@PathVariable Long id, @RequestBody PgOrderRequest request) {
        request.setId(id);
        return Result.ok(service.updateOrder(request));
    }

    /** 删除单个订单头；数据库是否允许删除仍受实际外键约束决定。 */
    @DeleteMapping("/orders/{id}")
    public Result<Boolean> deleteOrder(@PathVariable Long id) {
        return Result.ok(service.deleteOrder(id));
    }

    // ==================== products 完整 CRUD ====================

    /** 创建商品，Service 会校验名称、非负价格和非负库存。 */
    @PostMapping("/products")
    public Result<Long> createProduct(@RequestBody PgProductRequest request) {
        return Result.ok(service.createProduct(request));
    }

    /** 按商品主键查询当前价格与库存。 */
    @GetMapping("/products/{id}")
    public Result<PgProductResponse> getProduct(@PathVariable Long id) {
        return Result.ok(service.getProductResponse(id));
    }

    /** 查询全部商品；可与 FULL OUTER JOIN 中的未售商品结果对照学习。 */
    @GetMapping("/products")
    public Result<List<PgProductResponse>> listProducts() {
        return Result.ok(service.listProductsResponse());
    }

    /** 局部更新商品，允许单独改名、改价或调整库存，但不允许负值。 */
    @PutMapping("/products/{id}")
    public Result<Boolean> updateProduct(@PathVariable Long id, @RequestBody PgProductRequest request) {
        request.setId(id);
        return Result.ok(service.updateProduct(request));
    }

    /** 删除商品；FULL OUTER JOIN 案例可用于观察历史关联中的孤儿引用。 */
    @DeleteMapping("/products/{id}")
    public Result<Boolean> deleteProduct(@PathVariable Long id) {
        return Result.ok(service.deleteProduct(id));
    }

    // ==================== order_products 完整 CRUD ====================

    /** 创建订单商品关联，表中保存成交时的数量与单价。 */
    @PostMapping("/order-products")
    public Result<Long> createOrderProduct(@RequestBody PgOrderProductRequest request) {
        return Result.ok(service.createOrderProduct(request));
    }

    /** 按关联表自身主键查询一条订单商品明细。 */
    @GetMapping("/order-products/{id}")
    public Result<PgOrderProductResponse> getOrderProduct(@PathVariable Long id) {
        return Result.ok(service.getOrderProductResponse(id));
    }

    /** 查询全部订单商品明细；它是订单和商品多对多关系的中间表。 */
    @GetMapping("/order-products")
    public Result<List<PgOrderProductResponse>> listOrderProducts() {
        return Result.ok(service.listOrderProductsResponse());
    }

    /** 局部更新关联；quantity 必须大于零，unitPrice 必须大于等于零。 */
    @PutMapping("/order-products/{id}")
    public Result<Boolean> updateOrderProduct(@PathVariable Long id,
                                              @RequestBody PgOrderProductRequest request) {
        request.setId(id);
        return Result.ok(service.updateOrderProduct(request));
    }

    /** 删除一条订单商品关联，不会直接回补库存，完整业务应使用专门事务流程。 */
    @DeleteMapping("/order-products/{id}")
    public Result<Boolean> deleteOrderProduct(@PathVariable Long id) {
        return Result.ok(service.deleteOrderProduct(id));
    }

    // ==================== 条件、分页与批量案例 ====================

    /** 动态组合 LIKE、等值、IN 和 IS NULL 条件查询用户。 */
    @PostMapping("/users/search")
    public Result<List<PgUserResponse>> searchUsers(@RequestBody(required = false) UserQueryRequest request) {
        return Result.ok(service.searchUsersResponse(request));
    }

    /** 对比 MyBatis LIMIT/OFFSET 与 MyBatis-Plus 分页插件。 */
    @GetMapping("/users/page")
    public Result<PageResult<PgUserResponse>> pageUsers(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.pageUsersResponse(pageNum, pageSize));
    }

    /** 对比 XML foreach 多 VALUES 与事务内多次 BaseMapper insert。 */
    @PostMapping("/users/batch")
    public Result<Boolean> batchCreateUsers(@RequestBody List<PgUserRequest> users) {
        return Result.ok(service.batchCreateUsersResponse(users));
    }

    /** 按 ID 集合批量更新用户状态。 */
    @PutMapping("/users/batch-status")
    public Result<Boolean> batchUpdateUserStatus(@RequestBody UserStatusUpdateRequest request) {
        return Result.ok(service.batchUpdateUserStatus(request));
    }

    /** 批量删除用户；空集合会被 Service 拒绝，避免生成非法 IN SQL。 */
    @DeleteMapping("/users/batch")
    public Result<Boolean> batchDeleteUsers(@RequestBody List<Long> ids) {
        return Result.ok(service.batchDeleteUsers(ids));
    }

    /** 动态组合用户、状态及金额上下界查询订单。 */
    @PostMapping("/orders/search")
    public Result<List<PgOrderResponse>> searchOrders(@RequestBody(required = false) OrderQueryRequest request) {
        return Result.ok(service.searchOrdersResponse(request));
    }

    /** 对比手写 COUNT + LIMIT/OFFSET 与 MyBatis-Plus Page 分页插件。 */
    @GetMapping("/orders/page")
    public Result<PageResult<PgOrderResponse>> pageOrders(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(service.pageOrdersResponse(pageNum, pageSize));
    }

    /** 带筛选条件批量更新订单状态，禁止无条件更新整张表。 */
    @PutMapping("/orders/status/by-condition")
    public Result<Boolean> updateOrderStatusByCondition(@RequestBody OrderStatusUpdateRequest request) {
        return Result.ok(service.updateOrderStatusByCondition(request));
    }

    /** 对比 SQL BETWEEN 与 LambdaQueryWrapper between 的商品价格范围查询。 */
    @PostMapping("/products/search")
    public Result<List<PgProductResponse>> searchProducts(@RequestBody(required = false) ProductQueryRequest request) {
        return Result.ok(service.searchProductsResponse(request));
    }

    // ==================== 六种 JOIN 学习案例 ====================

    /** INNER JOIN：订单、用户、订单明细、商品四表均匹配才返回。 */
    @GetMapping("/joins/inner/order-details")
    public Result<List<OrderDetailResponse>> innerOrderDetails(@RequestParam String orderNo) {
        return Result.ok(service.getInnerOrderDetailsResponse(orderNo));
    }

    /** LEFT JOIN：保留所有用户，未认证用户的身份证字段为空。 */
    @GetMapping("/joins/left/users-id-cards")
    public Result<List<UserWithIdCardResponse>> leftUsersWithIdCard() {
        return Result.ok(service.getLeftUsersWithIdCardResponse());
    }

    /** RIGHT JOIN：保留右侧所有用户，没有订单时订单字段为空。 */
    @GetMapping("/joins/right/users-orders")
    public Result<List<UserOrderResponse>> rightUsersWithOrders() {
        return Result.ok(service.getRightUsersWithOrdersResponse());
    }

    /** FULL OUTER JOIN：审计未售商品和引用不到商品的订单明细。 */
    @GetMapping("/joins/full/products-order-items")
    public Result<List<ProductOrderAuditResponse>> fullProductOrderAudit() {
        return Result.ok(service.getFullProductOrderAuditResponse());
    }

    /** CROSS JOIN：生成受 limit 保护的用户商品笛卡尔积。 */
    @GetMapping("/joins/cross/user-products")
    public Result<List<UserProductCandidateResponse>> crossUserProductCandidates(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.getCrossUserProductCandidatesResponse(limit));
    }

    /** PostgreSQL LATERAL JOIN：查询每个用户最新一笔订单。 */
    @GetMapping("/joins/lateral/latest-orders")
    public Result<List<UserOrderResponse>> latestOrderPerUser() {
        return Result.ok(service.getLatestOrderPerUserResponse());
    }

    // ==================== 聚合、子查询、函数与高级 SQL ====================

    /** GROUP BY：统计每个用户的订单数和消费总额。 */
    @GetMapping("/queries/user-order-stats")
    public Result<List<UserOrderStatResponse>> userOrderStats() {
        return Result.ok(service.getUserOrderStatsResponse());
    }

    /** 聚合子查询与 LIMIT：查询消费金额前 N 的用户。 */
    @GetMapping("/queries/top-spending-users")
    public Result<List<UserOrderStatResponse>> topSpendingUsers(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(service.getTopSpendingUsersResponse(limit));
    }

    /** EXISTS：查询至少拥有一笔订单的用户。 */
    @GetMapping("/queries/users-with-orders")
    public Result<List<UserSimpleResponse>> usersWithOrders() {
        return Result.ok(service.getUsersWithOrdersResponse());
    }

    /** NOT EXISTS：查询完全没有订单的用户。 */
    @GetMapping("/queries/users-without-orders")
    public Result<List<UserSimpleResponse>> usersWithoutOrders() {
        return Result.ok(service.getUsersWithoutOrdersResponse());
    }

    /** HAVING：在聚合后按订单数量过滤用户。 */
    @GetMapping("/queries/users-by-order-count")
    public Result<List<UserOrderStatResponse>> usersByOrderCount(
            @RequestParam(defaultValue = "1") int minOrderCount) {
        return Result.ok(service.getUsersByOrderCountResponse(minOrderCount));
    }

    /** UNION ALL：拼接互斥的活跃用户与非活跃用户查询结果。 */
    @GetMapping("/queries/users-union-all")
    public Result<List<UserSimpleResponse>> usersUnionAll() {
        return Result.ok(service.getUsersUnionAllResponse());
    }

    /** CASE WHEN：把订单状态码映射为中文名称。 */
    @GetMapping("/queries/order-status-names")
    public Result<List<OrderStatusResponse>> orderStatusWithName() {
        return Result.ok(service.getOrderStatusWithNameResponse());
    }

    /**
     * 枚举自动映射：数据库 status code 先转换为 Java 枚举，再平铺返回 code 和中文名称。
     *
     * <p>两套完整地址分别是：</p>
     * <ul>
     *     <li>{@code /api/playground/pg/mybatis/queries/order-status-enums}</li>
     *     <li>{@code /api/playground/pg/mybatis-plus/queries/order-status-enums}</li>
     * </ul>
     *
     * <p>响应仍使用 {@code status: "PAID"} 和 {@code statusName: "已支付"} 两个平铺字段，
     * 不把枚举序列化成嵌套对象，便于和上面的 CASE WHEN 接口直接比较。</p>
     */
    @GetMapping("/queries/order-status-enums")
    public Result<List<OrderStatusResponse>> orderStatusWithEnumMapping() {
        return Result.ok(service.getOrderStatusWithEnumMappingResponse());
    }

    /** COALESCE：手机号为空时返回“未填写”。 */
    @GetMapping("/queries/user-contacts")
    public Result<List<UserContactResponse>> usersWithCoalescePhone() {
        return Result.ok(service.getUsersWithCoalescePhoneResponse());
    }

    /** PostgreSQL 日期转换与年份提取。 */
    @GetMapping("/queries/user-date-stats")
    public Result<List<UserDateStatResponse>> userDateStats() {
        return Result.ok(service.getUserDateStatsResponse());
    }

    /** ROW_NUMBER 窗口函数：生成用户消费连续排名。 */
    @GetMapping("/queries/user-spending-rank")
    public Result<List<UserRankResponse>> userSpendingRank() {
        return Result.ok(service.getUserSpendingRankResponse());
    }

    /** CTE：先统计用户消费，再划分 HIGH、MEDIUM、LOW 等级。 */
    @GetMapping("/queries/user-spending-levels")
    public Result<List<UserSpendingLevelResponse>> userSpendingLevels() {
        return Result.ok(service.getUserSpendingLevelsResponse());
    }

    /** 商品维度 GROUP BY：统计销量、销售额并同时返回当前库存。 */
    @GetMapping("/queries/product-sales-stats")
    public Result<List<ProductSalesStatResponse>> productSalesStats() {
        return Result.ok(service.getProductSalesStatsResponse());
    }

    // ==================== 跨表事务 ====================

    /**
     * 创建完整订单：校验用户和商品、计算金额、写订单和明细、原子扣库存。
     * 任一步骤失败时由 playgroundTransactionManager 回滚全部写操作。
     */
    @PostMapping("/orders/complete")
    public Result<CompleteOrderResponse> createCompleteOrder(
            @Valid @RequestBody CompleteOrderCreateRequest request) {
        return Result.ok(service.createCompleteOrderResponse(request));
    }
}
