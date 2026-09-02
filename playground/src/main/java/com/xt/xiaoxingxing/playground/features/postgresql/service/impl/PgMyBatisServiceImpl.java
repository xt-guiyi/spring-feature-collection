package com.xt.xiaoxingxing.playground.features.postgresql.service.impl;

import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.CompleteOrderItemRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.OrderQueryRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.OrderStatusUpdateRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.ProductQueryRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.UserQueryRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.request.UserStatusUpdateRequest;
import com.xt.xiaoxingxing.playground.features.postgresql.dto.response.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgIdCard;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgProduct;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisOrderMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisProductMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisQueryMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisRelationMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.mapper.PgMyBatisUserMapper;
import com.xt.xiaoxingxing.playground.features.postgresql.service.PgMyBatisService;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.OrderDetailVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.OrderStatusVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.ProductOrderAuditVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.ProductSalesStatVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserContactVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserDateStatVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserOrderStatVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserOrderVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserProductCandidateVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserRankVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserSimpleVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserSpendingLevelVO;
import com.xt.xiaoxingxing.playground.features.postgresql.vo.UserWithIdCardVO;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 普通 MyBatis 学习实现。
 *
 * <p>单表操作、JOIN、聚合和窗口计算均由 XML SQL 完成。Service 主要负责参数边界、
 * 业务异常、分页对象封装以及跨多条写 SQL 的事务一致性。与 MyBatis-Plus 实现对比时，
 * 可以观察“数据库集中计算”与“Service 分步组装”的差异。</p>
 */
@Service
@RequiredArgsConstructor
public class PgMyBatisServiceImpl implements PgMyBatisService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LIMIT = 1000;

    private final PgMyBatisUserMapper userMapper;
    private final PgMyBatisOrderMapper orderMapper;
    private final PgMyBatisProductMapper productMapper;
    private final PgMyBatisRelationMapper relationMapper;
    private final PgMyBatisQueryMapper queryMapper;

    // ==================== users CRUD ====================

    @Override
    public Long createUser(PgUser user) {
        BusinessAssert.isTrue(user != null && BusinessAssert.hasText(user.getUsername()), "用户名不能为空");
        Long id = userMapper.insertUser(user);
        user.setId(id);
        return id;
    }

    @Override
    public PgUser getUser(Long id) {
        requirePositiveId(id, "用户ID");
        return BusinessAssert.notNull(userMapper.selectUserById(id), "用户不存在");
    }

    @Override
    public List<PgUser> listUsers() {
        return userMapper.selectAllUsers();
    }

    @Override
    public boolean updateUser(PgUser user) {
        BusinessAssert.isTrue(user != null && user.getId() != null, "用户ID不能为空");
        BusinessAssert.isTrue(user.getUsername() != null || user.getEmail() != null || user.getPhone() != null
                || user.getStatus() != null || user.getCreatedAt() != null, "至少提供一个用户更新字段");
        BusinessAssert.isTrue(user.getUsername() == null || BusinessAssert.hasText(user.getUsername()), "用户名不能是空白字符串");
        return BusinessAssert.affected(userMapper.updateUser(user), "用户不存在");
    }

    @Override
    public boolean deleteUser(Long id) {
        requirePositiveId(id, "用户ID");
        return userMapper.deleteUserById(id) > 0;
    }

    // ==================== id_cards CRUD ====================

    @Override
    public Long createIdCard(PgIdCard idCard) {
        BusinessAssert.isTrue(idCard != null && idCard.getUserId() != null, "身份证所属用户不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(idCard.getCardNumber()), "身份证号码不能为空");
        Long id = executeUniqueWrite(() -> relationMapper.insertIdCard(idCard), "该用户已有身份证记录");
        idCard.setId(id);
        return id;
    }

    @Override
    public PgIdCard getIdCard(Long id) {
        requirePositiveId(id, "身份证ID");
        return BusinessAssert.notNull(relationMapper.selectIdCardById(id), "身份证记录不存在");
    }

    @Override
    public List<PgIdCard> listIdCards() {
        return relationMapper.selectAllIdCards();
    }

    @Override
    public boolean updateIdCard(PgIdCard idCard) {
        BusinessAssert.isTrue(idCard != null && idCard.getId() != null, "身份证ID不能为空");
        BusinessAssert.isTrue(idCard.getUserId() != null || idCard.getCardNumber() != null || idCard.getRealName() != null,
                "至少提供一个身份证更新字段");
        BusinessAssert.isTrue(idCard.getCardNumber() == null || BusinessAssert.hasText(idCard.getCardNumber()), "身份证号码不能是空白字符串");
        return BusinessAssert.affected(executeUniqueWrite(() -> relationMapper.updateIdCard(idCard),
                "该用户已有身份证记录"), "身份证记录不存在");
    }

    @Override
    public boolean deleteIdCard(Long id) {
        requirePositiveId(id, "身份证ID");
        return relationMapper.deleteIdCardById(id) > 0;
    }

    // ==================== orders CRUD ====================

    @Override
    public Long createOrder(PgOrder order) {
        BusinessAssert.isTrue(order != null && order.getUserId() != null, "订单所属用户不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(order.getOrderNo()), "订单号不能为空");
        BusinessAssert.isTrue(order.getTotalAmount() == null || order.getTotalAmount().signum() >= 0, "订单金额不能为负数");
        Long id = executeUniqueWrite(() -> orderMapper.insertOrder(order), "订单号已存在");
        order.setId(id);
        return id;
    }

    @Override
    public PgOrder getOrder(Long id) {
        requirePositiveId(id, "订单ID");
        return BusinessAssert.notNull(orderMapper.selectOrderById(id), "订单不存在");
    }

    @Override
    public List<PgOrder> listOrders() {
        return orderMapper.selectAllOrders();
    }

    @Override
    public boolean updateOrder(PgOrder order) {
        BusinessAssert.isTrue(order != null && order.getId() != null, "订单ID不能为空");
        BusinessAssert.isTrue(order.getUserId() != null || order.getOrderNo() != null || order.getTotalAmount() != null
                || order.getStatus() != null || order.getCreatedAt() != null, "至少提供一个订单更新字段");
        BusinessAssert.isTrue(order.getOrderNo() == null || BusinessAssert.hasText(order.getOrderNo()), "订单号不能是空白字符串");
        BusinessAssert.isTrue(order.getTotalAmount() == null || order.getTotalAmount().signum() >= 0, "订单金额不能为负数");
        return BusinessAssert.affected(executeUniqueWrite(() -> orderMapper.updateOrder(order), "订单号已存在"),
                "订单不存在");
    }

    @Override
    public boolean deleteOrder(Long id) {
        requirePositiveId(id, "订单ID");
        return orderMapper.deleteOrderById(id) > 0;
    }

    // ==================== products CRUD ====================

    @Override
    public Long createProduct(PgProduct product) {
        BusinessAssert.isTrue(product != null && BusinessAssert.hasText(product.getName()), "商品名称不能为空");
        BusinessAssert.isTrue(product.getPrice() != null && product.getPrice().signum() >= 0, "商品价格不能为负数");
        BusinessAssert.isTrue(product.getStock() != null && product.getStock() >= 0, "商品库存不能为负数");
        Long id = productMapper.insertProduct(product);
        product.setId(id);
        return id;
    }

    @Override
    public PgProduct getProduct(Long id) {
        requirePositiveId(id, "商品ID");
        return BusinessAssert.notNull(productMapper.selectProductById(id), "商品不存在");
    }

    @Override
    public List<PgProduct> listProducts() {
        return productMapper.selectAllProducts();
    }

    @Override
    public boolean updateProduct(PgProduct product) {
        BusinessAssert.isTrue(product != null && product.getId() != null, "商品ID不能为空");
        BusinessAssert.isTrue(product.getName() != null || product.getPrice() != null || product.getStock() != null,
                "至少提供一个商品更新字段");
        BusinessAssert.isTrue(product.getName() == null || BusinessAssert.hasText(product.getName()), "商品名称不能是空白字符串");
        BusinessAssert.isTrue(product.getPrice() == null || product.getPrice().signum() >= 0, "商品价格不能为负数");
        BusinessAssert.isTrue(product.getStock() == null || product.getStock() >= 0, "商品库存不能为负数");
        return BusinessAssert.affected(productMapper.updateProduct(product), "商品不存在");
    }

    @Override
    public boolean deleteProduct(Long id) {
        requirePositiveId(id, "商品ID");
        return productMapper.deleteProductById(id) > 0;
    }

    // ==================== order_products CRUD ====================

    @Override
    public Long createOrderProduct(PgOrderProduct orderProduct) {
        validateOrderProduct(orderProduct);
        Long id = executeUniqueWrite(() -> relationMapper.insertOrderProduct(orderProduct),
                "同一订单中不能重复添加相同商品");
        orderProduct.setId(id);
        return id;
    }

    @Override
    public PgOrderProduct getOrderProduct(Long id) {
        requirePositiveId(id, "订单商品ID");
        return BusinessAssert.notNull(relationMapper.selectOrderProductById(id), "订单商品记录不存在");
    }

    @Override
    public List<PgOrderProduct> listOrderProducts() {
        return relationMapper.selectAllOrderProducts();
    }

    @Override
    public boolean updateOrderProduct(PgOrderProduct orderProduct) {
        BusinessAssert.isTrue(orderProduct != null && orderProduct.getId() != null, "订单商品ID不能为空");
        BusinessAssert.isTrue(orderProduct.getOrderId() != null || orderProduct.getProductId() != null
                || orderProduct.getQuantity() != null || orderProduct.getUnitPrice() != null,
                "至少提供一个订单商品更新字段");
        BusinessAssert.isTrue(orderProduct.getQuantity() == null || orderProduct.getQuantity() > 0, "商品数量必须大于0");
        BusinessAssert.isTrue(orderProduct.getUnitPrice() == null || orderProduct.getUnitPrice().signum() >= 0,
                "商品单价不能为负数");
        return BusinessAssert.affected(executeUniqueWrite(() -> relationMapper.updateOrderProduct(orderProduct),
                "同一订单中不能重复添加相同商品"), "订单商品记录不存在");
    }

    @Override
    public boolean deleteOrderProduct(Long id) {
        requirePositiveId(id, "订单商品ID");
        return relationMapper.deleteOrderProductById(id) > 0;
    }

    // ==================== 条件、分页与批量 ====================

    @Override
    public List<PgUser> searchUsers(UserQueryRequest request) {
        return userMapper.selectUsersByCondition(request == null ? new UserQueryRequest() : request);
    }

    @Override
    public PageResult<PgUser> pageUsers(int pageNum, int pageSize) {
        /*
         * 实现步骤：
         * 1. 校验页码和每页数量；
         * 2. 查询符合范围的用户总数；
         * 3. 计算 offset 并查询当前页记录；
         * 4. 封装为统一 PageResult。
         */

        // 第1步：在执行 COUNT 和分页查询前统一校验分页边界。
        validatePage(pageNum, pageSize);

        // 第2步：总记录数用于计算总页数和判断是否还有后续数据。
        long total = userMapper.countUsers();

        // 第3步：页码从1开始，SQL OFFSET 从0开始，因此使用 (pageNum - 1) × pageSize。
        long offset = (long) (pageNum - 1) * pageSize;

        // 第4步：执行当前页查询，并与总数、页码信息一起封装。
        return pageResult(userMapper.selectUserPage(offset, pageSize), total, pageNum, pageSize);
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean batchCreateUsers(List<PgUser> users) {
        /*
         * 实现步骤：
         * 1. 校验批次不能为空；
         * 2. 校验批次中每个用户和用户名；
         * 3. 使用一条多组 VALUES 的 SQL 批量插入；
         * 4. 比较影响行数与请求数量，判断是否完整写入。
         */

        // 第1步：拒绝空批次，避免生成没有 VALUES 数据的 INSERT。
        BusinessAssert.isTrue(users != null && !users.isEmpty(), "用户列表不能为空");

        // 第2步：SQL 下发前一次性完成全部元素校验，防止批次中途才发现非法用户。
        users.forEach(user -> BusinessAssert.isTrue(user != null && BusinessAssert.hasText(user.getUsername()), "批量用户的用户名不能为空"));

        // 第3步：XML foreach 生成一个 INSERT 的多组 VALUES，一次数据库往返写入全部用户。
        // 第4步：只有影响行数等于请求数量，才认为整个批次完整成功。
        return userMapper.batchInsertUsers(users) == users.size();
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean batchUpdateUserStatus(UserStatusUpdateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验待更新用户ID集合；
         * 2. 校验目标状态；
         * 3. 执行 IN 条件批量更新并根据影响行数返回结果。
         */

        // 第1步：ID集合决定安全更新范围，不能为空。
        BusinessAssert.isTrue(request != null && request.getIds() != null && !request.getIds().isEmpty(), "用户ID列表不能为空");

        // 第2步：目标状态必须是有内容的字符串。
        BusinessAssert.isTrue(BusinessAssert.hasText(request.getStatus()), "用户状态不能为空");

        // 第3步：XML 使用 foreach 生成 IN 列表，影响行数大于0表示至少命中一名用户。
        return userMapper.batchUpdateUserStatus(request.getIds(), request.getStatus()) > 0;
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean batchDeleteUsers(List<Long> ids) {
        /*
         * 实现步骤：
         * 1. 校验待删除用户ID集合；
         * 2. 执行 IN 条件批量删除并根据影响行数返回结果。
         */

        // 第1步：拒绝空ID集合，避免生成非法或范围不明确的 DELETE。
        BusinessAssert.isTrue(ids != null && !ids.isEmpty(), "用户ID列表不能为空");

        // 第2步：影响行数大于0表示至少有一名实际存在的用户被删除。
        return userMapper.batchDeleteUsers(ids) > 0;
    }

    @Override
    public List<PgOrder> searchOrders(OrderQueryRequest request) {
        return orderMapper.selectOrdersByCondition(validateOrderQuery(request));
    }

    @Override
    public PageResult<PgOrder> pageOrders(int pageNum, int pageSize) {
        /*
         * 实现步骤：
         * 1. 校验页码和每页数量；
         * 2. 查询订单总数；
         * 3. 计算 offset 并查询当前页订单；
         * 4. 封装为统一 PageResult。
         */

        // 第1步：统一拒绝非法分页参数。
        validatePage(pageNum, pageSize);

        // 第2步：单独执行 COUNT，为分页响应提供总记录数。
        long total = orderMapper.countOrders();

        // 第3步：把从1开始的业务页码转换成从0开始的 SQL 偏移量。
        long offset = (long) (pageNum - 1) * pageSize;

        // 第4步：查询当前页并与总数、页码一起封装。
        return pageResult(orderMapper.selectOrderPage(offset, pageSize), total, pageNum, pageSize);
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean updateOrderStatusByCondition(OrderStatusUpdateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验目标状态、至少一个筛选条件和金额边界；
         * 2. 将请求交给 XML 动态 SQL，只拼接已提供的筛选条件；
         * 3. 根据影响行数判断是否更新到订单。
         */

        // 第1步：先建立安全边界，禁止没有 WHERE 筛选条件的整表状态更新。
        BusinessAssert.isTrue(request != null && BusinessAssert.hasText(request.getNewStatus()), "新订单状态不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(request.getOldStatus()) || request.getUserId() != null || request.getMinAmount() != null,
                "至少提供一个订单筛选条件，禁止无条件更新全表");
        BusinessAssert.isTrue(request.getMinAmount() == null || request.getMinAmount().signum() >= 0, "最小订单金额不能为负数");

        // 第2步：XML <if> 根据非空字段生成旧状态、用户和最小金额条件。
        // 第3步：影响行数为0表示没有订单同时满足这些条件。
        return orderMapper.updateOrderStatusByCondition(request) > 0;
    }

    @Override
    public List<PgProduct> searchProducts(ProductQueryRequest request) {
        return productMapper.selectProductsByCondition(validateProductQuery(request));
    }

    // ==================== JOIN 和高级查询：每个方法对应一条 XML SQL ====================

    @Override
    public List<OrderDetailVO> getInnerOrderDetails(String orderNo) {
        BusinessAssert.isTrue(BusinessAssert.hasText(orderNo), "订单号不能为空");
        return queryMapper.selectInnerOrderDetails(orderNo);
    }

    @Override
    public List<UserWithIdCardVO> getLeftUsersWithIdCard() {
        return queryMapper.selectLeftUsersWithIdCard();
    }

    @Override
    public List<UserOrderVO> getRightUsersWithOrders() {
        return queryMapper.selectRightUsersWithOrders();
    }

    @Override
    public List<ProductOrderAuditVO> getFullProductOrderAudit() {
        return queryMapper.selectFullProductOrderAudit();
    }

    @Override
    public List<UserProductCandidateVO> getCrossUserProductCandidates(int limit) {
        validateLimit(limit);
        return queryMapper.selectCrossUserProductCandidates(limit);
    }

    @Override
    public List<UserOrderVO> getLatestOrderPerUser() {
        return queryMapper.selectLatestOrderPerUser();
    }

    @Override
    public List<UserOrderStatVO> getUserOrderStats() {
        return queryMapper.selectUserOrderStats();
    }

    @Override
    public List<UserOrderStatVO> getTopSpendingUsers(int limit) {
        validateLimit(limit);
        return queryMapper.selectTopSpendingUsers(limit);
    }

    @Override
    public List<UserSimpleVO> getUsersWithOrders() {
        return queryMapper.selectUsersWithOrders();
    }

    @Override
    public List<UserSimpleVO> getUsersWithoutOrders() {
        return queryMapper.selectUsersWithoutOrders();
    }

    @Override
    public List<UserOrderStatVO> getUsersByOrderCount(int minOrderCount) {
        BusinessAssert.isTrue(minOrderCount >= 0, "最小订单数不能为负数");
        return queryMapper.selectUsersByOrderCount(minOrderCount);
    }

    @Override
    public List<UserSimpleVO> getUsersUnionAll() {
        return queryMapper.selectUsersUnionAll();
    }

    @Override
    public List<OrderStatusVO> getOrderStatusWithName() {
        return queryMapper.selectOrderStatusWithName();
    }

    @Override
    public List<OrderStatusVO> getOrderStatusWithEnumMapping() {
        /*
         * 实现步骤：
         * 1. 通过 XML 查询原始状态码，并在结果映射阶段转换为枚举；
         * 2. 对每条结果处理 NULL 状态，或从枚举读取 code/text；
         * 3. 组装 status/statusName 平铺响应。
         */

        // 第1步：查询只包含订单ID、订单号和枚举状态的专用投影。
        // XML 只查询 orders.status 原始 code。普通 MyBatis Mapper 与 Plus Mapper 共用
        // MyBatisConfig 创建的 MybatisConfiguration，因此进入 Service 时，
        // status 已经由 MyBatis-Plus 枚举类型处理器转换成 OrderStatusEnum，
        // 不需要再通过 switch 判断字符串。
        return queryMapper.selectOrderStatusWithEnumMapping().stream().map(order -> {
            // 第2步：枚举为空时保持NULL语义，否则分别读取数据库code和展示text。
            OrderStatusVO vo = new OrderStatusVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());

            // 对于正常状态，code/text 分别形成扁平响应中的 status/statusName；
            // 数据库列允许为 NULL，因此这里仍保留与旧 CASE WHEN 案例一致的空值语义。
            if (order.getStatus() == null) {
                vo.setStatus(null);
                vo.setStatusName("未知");
            } else {
                vo.setStatus(order.getStatus().getCode());
                vo.setStatusName(order.getStatus().getText());
            }

            // 第3步：枚举不直接暴露给接口，响应继续使用两个字符串字段。
            return vo;
        }).toList();
    }

    @Override
    public List<UserContactVO> getUsersWithCoalescePhone() {
        return queryMapper.selectUsersWithCoalescePhone();
    }

    @Override
    public List<UserDateStatVO> getUserDateStats() {
        return queryMapper.selectUserDateStats();
    }

    @Override
    public List<UserRankVO> getUserSpendingRank() {
        return queryMapper.selectUserSpendingRank();
    }

    @Override
    public List<UserSpendingLevelVO> getUserSpendingLevels() {
        return queryMapper.selectUserSpendingLevels();
    }

    @Override
    public List<ProductSalesStatVO> getProductSalesStats() {
        return queryMapper.selectProductSalesStats();
    }

    // ==================== 跨表事务 ====================

    /**
     * 使用普通 MyBatis XML 完成一次完整下单。
     *
     * <p>该方法不是单纯插入 orders，而是一个跨 users、products、orders、order_products
     * 四张表的事务流程。参数校验、订单头写入、订单明细写入和库存扣减必须作为一个整体：
     * 任意一步抛出异常，playgroundTransactionManager 都会回滚此前已经执行的 SQL，避免出现
     * “有订单但没有明细”或者“订单失败但库存已经扣除”的中间状态。</p>
     *
     * <p>主要步骤：校验请求与用户 → 合并重复商品 → 批量加载商品 → 计算可信金额 →
     * 创建订单头 → 批量写入成交快照 → 原子扣减库存 → 组装响应。</p>
     */
    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public CompleteOrderResponse createCompleteOrder(CompleteOrderCreateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验请求结构并确认下单用户存在；
         * 2. 合并请求中重复商品的购买数量；
         * 3. 批量查询全部商品并确认商品完整存在；
         * 4. 使用数据库价格、库存计算可信订单金额；
         * 5. 构造并写入订单头；
         * 6. 构造订单明细价格快照，并按商品ID排序；
         * 7. 批量写入全部明细，再按固定顺序原子扣减库存；
         * 8. 全部操作成功后组装响应，由事务统一提交。
         */

        // 第1步：先做不访问数据库的结构校验，再确认用户确实存在。
        // 越早拒绝空订单号、空商品列表和非法数量，越能减少无意义的数据库操作。
        validateCompleteOrderRequest(request);
        BusinessAssert.notNull(userMapper.selectUserById(request.getUserId()), "下单用户不存在");

        // 第2步：把请求中重复出现的商品合并成“productId -> 总数量”。
        // 例如商品 10 分别出现数量 2 和 3，合并后只保留 10 -> 5。
        // 这样既不会违反 (order_id, product_id) 唯一约束，也只需对该商品扣减一次库存。
        Map<Long, Integer> quantities = mergeQuantities(request.getItems());

        // 第3步：使用一条 WHERE id IN (...) 批量加载商品，避免逐商品查询形成 N+1，
        // 并通过查询结果确认请求中的商品全部存在。
        List<PgProduct> products = productMapper.selectProductsByIds(new ArrayList<>(quantities.keySet()));

        // 转成 Map 后，后续可按 productId O(1) 找到商品，同时也方便比较请求 ID 数和查询结果数。
        Map<Long, PgProduct> productMap = products.stream()
                .collect(Collectors.toMap(PgProduct::getId, Function.identity()));

        // 当前示例使用逻辑外键，IN 查询少返回一行就说明请求中包含不存在的商品。
        BusinessAssert.isTrue(productMap.size() == quantities.size(), "订单中包含不存在的商品");

        // 第4步：价格必须以数据库当前值为准，不能相信客户端上传的单价，
        // 否则调用者可以篡改价格。订单总额 = Σ(数据库商品单价 × 合并后的购买数量)。
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            PgProduct product = productMap.get(entry.getKey());

            // 这里的库存判断用于尽早返回具体商品名称，改善错误提示。
            // 它不能单独防止并发超卖，真正的并发保护在后面的原子 UPDATE 中。
            BusinessAssert.isTrue(product.getStock() != null && product.getStock() >= entry.getValue(),
                    "商品库存不足: " + product.getName());
            BusinessAssert.isTrue(product.getPrice() != null, "商品价格不能为空: " + product.getName());
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        // 第5步：创建订单头。此时金额已经由服务端根据数据库价格计算完成。
        // PENDING 表示订单刚创建但尚未支付；XML 使用 PostgreSQL RETURNING id 直接返回新订单主键。
        PgOrder order = new PgOrder();
        order.setUserId(request.getUserId());
        order.setOrderNo(request.getOrderNo());
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());

        // orders.order_no 有唯一约束。若重复，Spring DuplicateKeyException 会被转换成易理解的业务异常；
        // BusinessException 离开事务方法后，也会触发整个下单事务回滚。
        Long orderId = executeUniqueWrite(() -> orderMapper.insertOrder(order), "订单号已存在");

        // 第6步：构造订单明细并按商品ID排序。unitPrice 保存的是“下单时成交价快照”，
        // 以后 products.price 即使涨价或降价，也不会改变历史订单金额。
        List<PgOrderProduct> orderProducts = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            PgProduct product = productMap.get(entry.getKey());
            PgOrderProduct item = new PgOrderProduct();
            item.setOrderId(orderId);
            item.setProductId(product.getId());
            item.setQuantity(entry.getValue());
            item.setUnitPrice(product.getPrice());
            orderProducts.add(item);
        }

        // 多个并发订单可能购买相同的一组商品。如果事务 A 按“商品 1 → 商品 2”扣库存，
        // 事务 B 却按“商品 2 → 商品 1”扣库存，就可能互相等待对方已经持有的行锁并形成死锁。
        // 因此统一按 productId 升序处理，让所有下单事务尽量以相同顺序获取 UPDATE 的行级排他锁。
        // 这不会改变订单金额或明细内容，只是固定写入和扣库存顺序，从而降低循环等待和死锁概率。
        orderProducts.sort(Comparator.comparing(PgOrderProduct::getProductId));

        // 第7步：先批量写入全部明细，再逐商品执行原子库存扣减。
        // 普通 MyBatis XML 通过 foreach 生成一条 INSERT 的多组 VALUES，
        // 一次数据库往返写入全部明细。受影响行数必须等于明细数，否则主动抛异常回滚。
        int inserted = executeUniqueWrite(() -> relationMapper.batchInsertOrderProducts(orderProducts),
                "同一订单中不能重复添加相同商品");
        BusinessAssert.isTrue(inserted == orderProducts.size(), "订单明细写入不完整");

        // 随后逐商品执行原子库存扣减。对应 XML 的核心条件为：
        // UPDATE products SET stock = stock - quantity
        // WHERE id = productId AND stock >= quantity
        // “判断库存”和“扣减库存”处于同一条 SQL，多个事务并发时只有库存仍充足的请求能影响一行。
        for (PgOrderProduct item : orderProducts) {
            int affected = productMapper.decrementStock(item.getProductId(), item.getQuantity());

            // affected=0 说明商品被删除，或其他事务已先一步消耗库存。
            // 抛出异常后，本方法之前插入的订单头和所有明细都会一起回滚。
            BusinessAssert.isTrue(affected == 1, "商品库存已发生变化，请重试");
        }

        // 第8步：只有全部 SQL 均成功后才组装响应。事务将在方法正常返回后提交。
        // itemCount 是商品件数之和，不是不同商品种类数 quantities.size()。
        CompleteOrderResponse response = new CompleteOrderResponse();
        response.setOrderId(orderId);
        response.setOrderNo(request.getOrderNo());
        response.setTotalAmount(totalAmount);
        response.setItemCount(sumItemCount(quantities));
        return response;
    }

    // ==================== 公共校验与结果封装 ====================

    private void validateOrderProduct(PgOrderProduct item) {
        BusinessAssert.isTrue(item != null && item.getOrderId() != null, "订单ID不能为空");
        BusinessAssert.isTrue(item.getProductId() != null, "商品ID不能为空");
        BusinessAssert.isTrue(item.getQuantity() != null && item.getQuantity() > 0, "商品数量必须大于0");
        BusinessAssert.isTrue(item.getUnitPrice() != null && item.getUnitPrice().signum() >= 0, "商品单价不能为负数");
    }

    private void validateCompleteOrderRequest(CompleteOrderCreateRequest request) {
        BusinessAssert.isTrue(request != null && request.getUserId() != null, "下单用户不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(request.getOrderNo()), "订单号不能为空");
        BusinessAssert.isTrue(request.getItems() != null && !request.getItems().isEmpty(), "订单商品不能为空");
        request.getItems().forEach(item -> BusinessAssert.isTrue(item != null && item.getProductId() != null
                && item.getQuantity() != null && item.getQuantity() > 0, "订单商品参数不合法"));
    }

    private Map<Long, Integer> mergeQuantities(List<CompleteOrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CompleteOrderItemRequest item : items) {
            long mergedQuantity = (long) quantities.getOrDefault(item.getProductId(), 0) + item.getQuantity();
            BusinessAssert.isTrue(mergedQuantity <= Integer.MAX_VALUE, "单个商品数量过大");
            quantities.put(item.getProductId(), (int) mergedQuantity);
        }
        return quantities;
    }

    /**
     * 响应字段使用 Integer，因此先用 long 累加，避免 Stream.mapToInt 在极端数据下静默溢出。
     */
    private int sumItemCount(Map<Long, Integer> quantities) {
        long total = quantities.values().stream().mapToLong(Integer::longValue).sum();
        BusinessAssert.isTrue(total <= Integer.MAX_VALUE, "订单商品总数量过大");
        return (int) total;
    }

    /**
     * 金额范围属于业务参数，必须在进入 SQL 前校验。否则 min 大于 max 时只会得到空集合，
     * 调用者无法区分“没有符合条件的数据”和“请求参数本身错误”。
     */
    private OrderQueryRequest validateOrderQuery(OrderQueryRequest request) {
        OrderQueryRequest query = request == null ? new OrderQueryRequest() : request;
        BusinessAssert.isTrue(query.getUserId() == null || query.getUserId() > 0, "用户ID必须大于0");
        BusinessAssert.isTrue(query.getMinAmount() == null || query.getMinAmount().signum() >= 0, "最小订单金额不能为负数");
        BusinessAssert.isTrue(query.getMaxAmount() == null || query.getMaxAmount().signum() >= 0, "最大订单金额不能为负数");
        BusinessAssert.isTrue(query.getMinAmount() == null || query.getMaxAmount() == null
                || query.getMinAmount().compareTo(query.getMaxAmount()) <= 0, "最小订单金额不能大于最大订单金额");
        return query;
    }

    /** 商品查询同样在 Service 统一校验，使普通 MyBatis 与 MyBatis-Plus 的错误行为完全一致。 */
    private ProductQueryRequest validateProductQuery(ProductQueryRequest request) {
        ProductQueryRequest query = request == null ? new ProductQueryRequest() : request;
        BusinessAssert.isTrue(query.getMinPrice() == null || query.getMinPrice().signum() >= 0, "最低价格不能为负数");
        BusinessAssert.isTrue(query.getMaxPrice() == null || query.getMaxPrice().signum() >= 0, "最高价格不能为负数");
        BusinessAssert.isTrue(query.getMinPrice() == null || query.getMaxPrice() == null
                || query.getMinPrice().compareTo(query.getMaxPrice()) <= 0, "最低价格不能大于最高价格");
        BusinessAssert.isTrue(query.getMinStock() == null || query.getMinStock() >= 0, "最低库存不能为负数");
        return query;
    }

    /**
     * Spring 会把不同数据库的重复键错误统一转换为 DuplicateKeyException，例如
     * PostgreSQL 的 SQLSTATE 23505 和 MySQL 的错误码 1062。Service 只依赖 Spring 的
     * 跨数据库异常类型，不再判断具体厂商错误码；外键、非空等其他完整性异常仍原样抛出。
     */
    private <T> T executeUniqueWrite(Supplier<T> action, String message) {
        try {
            return action.get();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(message);
        }
    }

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum >= 1, "页码必须大于等于1");
        BusinessAssert.isTrue(pageSize >= 1 && pageSize <= MAX_PAGE_SIZE, "每页数量必须在1到100之间");
    }

    private void validateLimit(int limit) {
        BusinessAssert.isTrue(limit >= 1 && limit <= MAX_QUERY_LIMIT, "查询数量必须在1到1000之间");
    }

    private void requirePositiveId(Long id, String fieldName) {
        BusinessAssert.isTrue(id != null && id > 0, fieldName + "必须大于0");
    }

    private <T> PageResult<T> pageResult(List<T> list, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

}
