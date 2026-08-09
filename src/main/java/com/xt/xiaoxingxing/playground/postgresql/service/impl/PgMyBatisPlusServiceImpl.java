package com.xt.xiaoxingxing.playground.postgresql.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderItemRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.OrderQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.OrderStatusUpdateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.ProductQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.UserQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.UserStatusUpdateRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgIdCard;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderStatusEnumDemo;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProduct;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgIdCardPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgOrderPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgOrderProductPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgOrderStatusEnumPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgProductPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.mapper.PgUserPlusMapper;
import com.xt.xiaoxingxing.playground.postgresql.service.PgMyBatisPlusService;
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
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 官方能力学习实现。
 *
 * <p>BaseMapper 和 Lambda Wrapper 适合类型安全的单表操作，但核心版不提供多表 JOIN DSL。
 * 因此本类对复杂场景采用“批量读取相关表，再用 Map/分组组装”的方式复现相同结果。
 * 这种实现清晰展示了技术边界：它通常产生更多 SQL、传输更多明细并占用应用内存，
 * 生产环境的大数据聚合一般仍应交给数据库 SQL。</p>
 */
@Service
@RequiredArgsConstructor
public class PgMyBatisPlusServiceImpl implements PgMyBatisPlusService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LIMIT = 1000;

    private final PgUserPlusMapper userMapper;
    private final PgIdCardPlusMapper idCardMapper;
    private final PgOrderPlusMapper orderMapper;
    private final PgProductPlusMapper productMapper;
    private final PgOrderProductPlusMapper orderProductMapper;
    private final PgOrderStatusEnumPlusMapper orderStatusEnumMapper;

    // ==================== users CRUD：BaseMapper 标准方法 ====================

    @Override
    public Long createUser(PgUser user) {
        BusinessAssert.isTrue(user != null && BusinessAssert.hasText(user.getUsername()), "用户名不能为空");
        BusinessAssert.isTrue(userMapper.insert(user) == 1, "用户创建失败");
        return user.getId();
    }

    @Override
    public PgUser getUser(Long id) {
        requirePositiveId(id, "用户ID");
        return BusinessAssert.notNull(userMapper.selectById(id), "用户不存在");
    }

    @Override
    public List<PgUser> listUsers() {
        return userMapper.selectList(Wrappers.<PgUser>lambdaQuery().orderByAsc(PgUser::getId));
    }

    @Override
    public boolean updateUser(PgUser user) {
        BusinessAssert.isTrue(user != null && user.getId() != null, "用户ID不能为空");
        BusinessAssert.isTrue(user.getUsername() != null || user.getEmail() != null || user.getPhone() != null
                || user.getStatus() != null || user.getCreatedAt() != null, "至少提供一个用户更新字段");
        BusinessAssert.isTrue(user.getUsername() == null || BusinessAssert.hasText(user.getUsername()), "用户名不能是空白字符串");
        return BusinessAssert.affected(userMapper.updateById(user), "用户不存在");
    }

    @Override
    public boolean deleteUser(Long id) {
        requirePositiveId(id, "用户ID");
        return userMapper.deleteById(id) > 0;
    }

    // ==================== id_cards CRUD ====================

    @Override
    public Long createIdCard(PgIdCard idCard) {
        BusinessAssert.isTrue(idCard != null && idCard.getUserId() != null, "身份证所属用户不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(idCard.getCardNumber()), "身份证号码不能为空");
        BusinessAssert.isTrue(executeUniqueWrite(() -> idCardMapper.insert(idCard),
                "该用户已有身份证记录") == 1, "身份证记录创建失败");
        return idCard.getId();
    }

    @Override
    public PgIdCard getIdCard(Long id) {
        requirePositiveId(id, "身份证ID");
        return BusinessAssert.notNull(idCardMapper.selectById(id), "身份证记录不存在");
    }

    @Override
    public List<PgIdCard> listIdCards() {
        return idCardMapper.selectList(Wrappers.<PgIdCard>lambdaQuery().orderByAsc(PgIdCard::getId));
    }

    @Override
    public boolean updateIdCard(PgIdCard idCard) {
        BusinessAssert.isTrue(idCard != null && idCard.getId() != null, "身份证ID不能为空");
        BusinessAssert.isTrue(idCard.getUserId() != null || idCard.getCardNumber() != null || idCard.getRealName() != null,
                "至少提供一个身份证更新字段");
        BusinessAssert.isTrue(idCard.getCardNumber() == null || BusinessAssert.hasText(idCard.getCardNumber()), "身份证号码不能是空白字符串");
        return BusinessAssert.affected(executeUniqueWrite(() -> idCardMapper.updateById(idCard),
                "该用户已有身份证记录"), "身份证记录不存在");
    }

    @Override
    public boolean deleteIdCard(Long id) {
        requirePositiveId(id, "身份证ID");
        return idCardMapper.deleteById(id) > 0;
    }

    // ==================== orders CRUD ====================

    @Override
    public Long createOrder(PgOrder order) {
        BusinessAssert.isTrue(order != null && order.getUserId() != null, "订单所属用户不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(order.getOrderNo()), "订单号不能为空");
        BusinessAssert.isTrue(order.getTotalAmount() == null || order.getTotalAmount().signum() >= 0, "订单金额不能为负数");
        BusinessAssert.isTrue(executeUniqueWrite(() -> orderMapper.insert(order), "订单号已存在") == 1, "订单创建失败");
        return order.getId();
    }

    @Override
    public PgOrder getOrder(Long id) {
        requirePositiveId(id, "订单ID");
        return BusinessAssert.notNull(orderMapper.selectById(id), "订单不存在");
    }

    @Override
    public List<PgOrder> listOrders() {
        return orderMapper.selectList(Wrappers.<PgOrder>lambdaQuery().orderByAsc(PgOrder::getId));
    }

    @Override
    public boolean updateOrder(PgOrder order) {
        BusinessAssert.isTrue(order != null && order.getId() != null, "订单ID不能为空");
        BusinessAssert.isTrue(order.getUserId() != null || order.getOrderNo() != null || order.getTotalAmount() != null
                || order.getStatus() != null || order.getCreatedAt() != null, "至少提供一个订单更新字段");
        BusinessAssert.isTrue(order.getOrderNo() == null || BusinessAssert.hasText(order.getOrderNo()), "订单号不能是空白字符串");
        BusinessAssert.isTrue(order.getTotalAmount() == null || order.getTotalAmount().signum() >= 0, "订单金额不能为负数");
        return BusinessAssert.affected(executeUniqueWrite(() -> orderMapper.updateById(order), "订单号已存在"),
                "订单不存在");
    }

    @Override
    public boolean deleteOrder(Long id) {
        requirePositiveId(id, "订单ID");
        return orderMapper.deleteById(id) > 0;
    }

    // ==================== products CRUD ====================

    @Override
    public Long createProduct(PgProduct product) {
        BusinessAssert.isTrue(product != null && BusinessAssert.hasText(product.getName()), "商品名称不能为空");
        BusinessAssert.isTrue(product.getPrice() != null && product.getPrice().signum() >= 0, "商品价格不能为负数");
        BusinessAssert.isTrue(product.getStock() != null && product.getStock() >= 0, "商品库存不能为负数");
        BusinessAssert.isTrue(executeUniqueWrite(() ->productMapper.insert(product),"不能重复添加相同商品") == 1, "商品创建失败");
        return product.getId();
    }

    @Override
    public PgProduct getProduct(Long id) {
        requirePositiveId(id, "商品ID");
        return BusinessAssert.notNull(productMapper.selectById(id), "商品不存在");
    }

    @Override
    public List<PgProduct> listProducts() {
        return productMapper.selectList(Wrappers.<PgProduct>lambdaQuery().orderByAsc(PgProduct::getId));
    }

    @Override
    public boolean updateProduct(PgProduct product) {
        BusinessAssert.isTrue(product != null && product.getId() != null, "商品ID不能为空");
        BusinessAssert.isTrue(product.getName() != null || product.getPrice() != null || product.getStock() != null,
                "至少提供一个商品更新字段");
        BusinessAssert.isTrue(product.getName() == null || BusinessAssert.hasText(product.getName()), "商品名称不能是空白字符串");
        BusinessAssert.isTrue(product.getPrice() == null || product.getPrice().signum() >= 0, "商品价格不能为负数");
        BusinessAssert.isTrue(product.getStock() == null || product.getStock() >= 0, "商品库存不能为负数");
        return BusinessAssert.affected(productMapper.updateById(product), "商品不存在");
    }

    @Override
    public boolean deleteProduct(Long id) {
        requirePositiveId(id, "商品ID");
        return productMapper.deleteById(id) > 0;
    }

    // ==================== order_products CRUD ====================

    @Override
    public Long createOrderProduct(PgOrderProduct orderProduct) {
        validateOrderProduct(orderProduct);
        BusinessAssert.isTrue(executeUniqueWrite(() -> orderProductMapper.insert(orderProduct),
                "同一订单中不能重复添加相同商品") == 1, "订单商品记录创建失败");
        return orderProduct.getId();
    }

    @Override
    public PgOrderProduct getOrderProduct(Long id) {
        requirePositiveId(id, "订单商品ID");
        return BusinessAssert.notNull(orderProductMapper.selectById(id), "订单商品记录不存在");
    }

    @Override
    public List<PgOrderProduct> listOrderProducts() {
        return orderProductMapper.selectList(Wrappers.<PgOrderProduct>lambdaQuery()
                .orderByAsc(PgOrderProduct::getId));
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
        return BusinessAssert.affected(executeUniqueWrite(() -> orderProductMapper.updateById(orderProduct),
                "同一订单中不能重复添加相同商品"), "订单商品记录不存在");
    }

    @Override
    public boolean deleteOrderProduct(Long id) {
        requirePositiveId(id, "订单商品ID");
        return orderProductMapper.deleteById(id) > 0;
    }

    // ==================== Wrapper、分页与批量 ====================

    @Override
    public List<PgUser> searchUsers(UserQueryRequest request) {
        /*
         * 实现步骤：
         * 1. 将空请求转换为空查询对象，保证后续条件读取安全；
         * 2. 按关键字、状态、ID集合和手机号空值要求动态构造查询条件；
         * 3. 添加稳定排序并执行查询。
         */

        // 第1步：统一得到非空查询对象；空请求表示不添加任何筛选条件。
        UserQueryRequest query = request == null ? new UserQueryRequest() : request;

        // 第2步：仅在参数存在时添加对应条件，未提供的条件不会出现在最终 SQL 中。
        LambdaQueryWrapper<PgUser> wrapper = Wrappers.lambdaQuery(PgUser.class);
        // and 将“用户名 OR 邮箱”包在一组括号中，避免后续状态条件被 OR 意外放宽。
        wrapper.and(BusinessAssert.hasText(query.getKeyword()), nested -> nested
                        .like(PgUser::getUsername, query.getKeyword())
                        .or()
                        .like(PgUser::getEmail, query.getKeyword()))
                .eq(BusinessAssert.hasText(query.getStatus()), PgUser::getStatus, query.getStatus())
                .in(query.getIds() != null && !query.getIds().isEmpty(), PgUser::getId, query.getIds())
                .isNull(Boolean.TRUE.equals(query.getPhoneIsNull()), PgUser::getPhone)
                // 第3步：先按状态、再按ID升序，保证同一组数据多次查询的顺序稳定。
                .orderByAsc(PgUser::getStatus)
                .orderByAsc(PgUser::getId);
        return userMapper.selectList(wrapper);
    }

    @Override
    public PageResult<PgUser> pageUsers(int pageNum, int pageSize) {
        /*
         * 实现步骤：
         * 1. 校验页码和每页数量；
         * 2. 创建分页对象并执行按用户ID稳定排序的分页查询；
         * 3. 转换为两套接口共享的 PageResult。
         */

        // 第1步：在访问数据库前拒绝非法页码和过大的每页数量。
        validatePage(pageNum, pageSize);

        // 第2步：Page 同时携带分页参数、总数和当前页记录，分页插件据此生成 SQL。
        IPage<PgUser> page = new Page<>(pageNum, pageSize);
        userMapper.selectPage(page, Wrappers.<PgUser>lambdaQuery().orderByAsc(PgUser::getId));

        // 第3步：隐藏 MyBatis-Plus 的 IPage 类型，对外保持统一分页响应。
        return pageResult(page);
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean batchCreateUsers(List<PgUser> users) {
        /*
         * 实现步骤：
         * 1. 校验批次本身不能为空；
         * 2. 逐个校验用户并通过 BaseMapper 插入，任一失败时回滚整个批次；
         * 3. 全部插入成功后返回 true。
         */

        // 第1步：空批次没有明确业务意义，也应避免开启无效事务。
        BusinessAssert.isTrue(users != null && !users.isEmpty(), "用户列表不能为空");

        // 第2步：Plus 核心版在这里逐条 insert；事务保证不会留下只成功一部分的批次。
        for (PgUser user : users) {
            BusinessAssert.isTrue(user != null && BusinessAssert.hasText(user.getUsername()), "批量用户的用户名不能为空");
            BusinessAssert.isTrue(userMapper.insert(user) == 1, "批量创建用户失败");
        }

        // 第3步：循环能够正常结束，说明每一行 insert 都影响了一行。
        return true;
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean batchUpdateUserStatus(UserStatusUpdateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验ID集合和目标状态；
         * 2. 构造基于 ID IN (...) 的批量状态更新；
         * 3. 执行更新并根据影响行数判断是否命中数据。
         */

        // 第1步：两个参数共同决定更新范围和更新值，任一缺失都不能执行。
        BusinessAssert.isTrue(request != null && request.getIds() != null && !request.getIds().isEmpty(), "用户ID列表不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(request.getStatus()), "用户状态不能为空");

        // 第2步：IN 限定更新范围，set 指定所有命中用户的新状态。
        LambdaUpdateWrapper<PgUser> wrapper = Wrappers.lambdaUpdate(PgUser.class)
                .in(PgUser::getId, request.getIds())
                .set(PgUser::getStatus, request.getStatus());

        // 第3步：影响行数大于0表示至少更新了一名实际存在的用户。
        return userMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean batchDeleteUsers(List<Long> ids) {
        /*
         * 实现步骤：
         * 1. 校验待删除ID集合；
         * 2. 执行批量删除并根据影响行数判断是否删除到数据。
         */

        // 第1步：拒绝空集合，避免生成没有明确删除范围的操作。
        BusinessAssert.isTrue(ids != null && !ids.isEmpty(), "用户ID列表不能为空");

        // 第2步：deleteByIds 生成按主键集合删除的 SQL，不在 Java 中逐条往返数据库。
        return userMapper.deleteByIds(ids) > 0;
    }

    @Override
    public List<PgOrder> searchOrders(OrderQueryRequest request) {
        /*
         * 实现步骤：
         * 1. 规范化请求并校验金额上下界；
         * 2. 根据用户、状态和金额范围动态构造查询条件；
         * 3. 执行查询后统一应用金额降序、NULL靠后和ID升序规则。
         */

        // 第1步：空请求转换为空条件，同时验证金额不能为负且上下界顺序正确。
        OrderQueryRequest query = validateOrderQuery(request);

        // 第2步：每个布尔参数决定对应条件是否真正进入 SQL。
        LambdaQueryWrapper<PgOrder> wrapper = Wrappers.lambdaQuery(PgOrder.class)
                .eq(query.getUserId() != null, PgOrder::getUserId, query.getUserId())
                .eq(BusinessAssert.hasText(query.getStatus()), PgOrder::getStatus, query.getStatus())
                .ge(query.getMinAmount() != null, PgOrder::getTotalAmount, query.getMinAmount())
                .le(query.getMaxAmount() != null, PgOrder::getTotalAmount, query.getMaxAmount())
                .orderByDesc(PgOrder::getTotalAmount)
                .orderByAsc(PgOrder::getId);

        // 第3步：查询并在 Java 中明确 NULLS LAST，确保与普通 MyBatis XML 语义一致。
        // PostgreSQL 的 DESC 默认会把 NULL 放在最前面；这里在 Java 中明确 NULLS LAST，
        // 与普通 MyBatis XML 的排序语义保持一致。
        return orderMapper.selectList(wrapper).stream()
                .sorted(Comparator.comparing(PgOrder::getTotalAmount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PgOrder::getId))
                .toList();
    }

    @Override
    public PageResult<PgOrder> pageOrders(int pageNum, int pageSize) {
        /*
         * 实现步骤：
         * 1. 校验页码和每页数量；
         * 2. 创建分页对象并按创建时间、订单ID倒序查询；
         * 3. 转换为共享 PageResult。
         */

        // 第1步：统一限制分页边界，防止负页码或一次拉取过多记录。
        validatePage(pageNum, pageSize);

        // 第2步：第二排序键 ID 用于解决创建时间相同情况下的顺序不确定性。
        IPage<PgOrder> page = new Page<>(pageNum, pageSize);
        orderMapper.selectPage(page, Wrappers.<PgOrder>lambdaQuery()
                .orderByDesc(PgOrder::getCreatedAt)
                .orderByDesc(PgOrder::getId));

        // 第3步：将框架分页对象转换为接口层统一模型。
        return pageResult(page);
    }

    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public boolean updateOrderStatusByCondition(OrderStatusUpdateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验新状态、筛选条件和金额边界，禁止无条件更新全表；
         * 2. 只把调用方提供的筛选条件加入 UpdateWrapper；
         * 3. 执行状态更新并根据影响行数返回结果。
         */

        // 第1步：必须同时具备更新值和至少一个安全筛选条件。
        BusinessAssert.isTrue(request != null && BusinessAssert.hasText(request.getNewStatus()), "新订单状态不能为空");
        BusinessAssert.isTrue(BusinessAssert.hasText(request.getOldStatus()) || request.getUserId() != null || request.getMinAmount() != null,
                "至少提供一个订单筛选条件，禁止无条件更新全表");
        BusinessAssert.isTrue(request.getMinAmount() == null || request.getMinAmount().signum() >= 0, "最小订单金额不能为负数");

        // 第2步：未提供的筛选项不会生成 SQL 条件，set 只负责写入新状态。
        LambdaUpdateWrapper<PgOrder> wrapper = Wrappers.lambdaUpdate(PgOrder.class)
                .eq(BusinessAssert.hasText(request.getOldStatus()), PgOrder::getStatus, request.getOldStatus())
                .eq(request.getUserId() != null, PgOrder::getUserId, request.getUserId())
                .ge(request.getMinAmount() != null, PgOrder::getTotalAmount, request.getMinAmount())
                .set(PgOrder::getStatus, request.getNewStatus());

        // 第3步：影响行数为0表示没有订单满足全部筛选条件。
        return orderMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<PgProduct> searchProducts(ProductQueryRequest request) {
        /*
         * 实现步骤：
         * 1. 规范化请求并校验价格、库存边界；
         * 2. 判断价格条件应使用 BETWEEN 还是单边界比较；
         * 3. 构造名称、价格、库存条件和稳定排序后执行查询。
         */

        // 第1步：空请求转换为空条件，非法负数或颠倒的价格区间提前失败。
        ProductQueryRequest query = validateProductQuery(request);

        // 第2步：上下界同时存在时用 BETWEEN，否则分别使用大于等于或小于等于。
        boolean hasPriceRange = query.getMinPrice() != null && query.getMaxPrice() != null;

        // 第3步：所有可选条件集中构造，并以价格降序、ID升序形成稳定结果。
        LambdaQueryWrapper<PgProduct> wrapper = Wrappers.lambdaQuery(PgProduct.class)
                .like(BusinessAssert.hasText(query.getKeyword()), PgProduct::getName, query.getKeyword())
                .between(hasPriceRange, PgProduct::getPrice, query.getMinPrice(), query.getMaxPrice())
                .ge(!hasPriceRange && query.getMinPrice() != null, PgProduct::getPrice, query.getMinPrice())
                .le(!hasPriceRange && query.getMaxPrice() != null, PgProduct::getPrice, query.getMaxPrice())
                .ge(query.getMinStock() != null, PgProduct::getStock, query.getMinStock())
                .orderByDesc(PgProduct::getPrice)
                .orderByAsc(PgProduct::getId);
        return productMapper.selectList(wrapper);
    }

    // ==================== 六种 JOIN 的 Java 等价组装 ====================

    @Override
    public List<OrderDetailVO> getInnerOrderDetails(String orderNo) {
        /*
         * 实现步骤：
         * 1. 校验订单号并查询订单；
         * 2. 查询订单所属用户，订单或用户缺失时按 INNER JOIN 语义返回空结果；
         * 3. 查询订单下的全部商品明细，明细为空时返回空结果；
         * 4. 根据明细中的商品ID集合批量查询商品并建立索引；
         * 5. 过滤商品不存在的明细并组装最终结果。
         */

        // 第1步：订单号是本次关联查询的入口，先定位唯一订单。
        BusinessAssert.isTrue(BusinessAssert.hasText(orderNo), "订单号不能为空");
        PgOrder order = orderMapper.selectOne(Wrappers.<PgOrder>lambdaQuery()
                .eq(PgOrder::getOrderNo, orderNo));
        if (order == null) {
            return List.of();
        }

        // 第2步：INNER JOIN 要求订单关联的用户必须存在，否则整组结果为空。
        PgUser user = userMapper.selectById(order.getUserId());
        if (user == null) {
            return List.of();
        }

        // 第3步：查询当前订单的全部明细，并保持与 XML 相同的明细ID顺序。
        List<PgOrderProduct> items = orderProductMapper.selectList(Wrappers.<PgOrderProduct>lambdaQuery()
                .eq(PgOrderProduct::getOrderId, order.getId())
                .orderByAsc(PgOrderProduct::getId));
        if (items.isEmpty()) {
            return List.of();
        }

        // 第4步：把所有商品ID合并成集合后一次批量查询，避免按明细逐条查询形成 N+1。
        Map<Long, PgProduct> products = productMapper.selectBatchIds(items.stream()
                        .map(PgOrderProduct::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PgProduct::getId, Function.identity()));

        // 第5步：INNER JOIN 语义要求四表关联完整，商品缺失的明细直接过滤而不是补空字段。
        return items.stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> toOrderDetail(order, user, item, products.get(item.getProductId())))
                .toList();
    }

    @Override
    public List<UserWithIdCardVO> getLeftUsersWithIdCard() {
        /*
         * 实现步骤：
         * 1. 查询完整用户列表，作为 LEFT JOIN 必须保留的左侧集合；
         * 2. 查询身份证记录并按 userId 建立索引；
         * 3. 遍历全部用户，存在身份证时填充关联字段，不存在时仍保留用户。
         */

        // 第1步：结果行数和保留范围由完整用户列表决定。
        List<PgUser> users = listUsers();

        // 第2步：按 userId 建立一对一索引，避免遍历每个用户时重复扫描身份证列表。
        Map<Long, PgIdCard> cardsByUser = listIdCards().stream()
                .collect(Collectors.toMap(PgIdCard::getUserId, Function.identity(), (left, right) -> left));

        // 第3步：无身份证用户同样构造 VO，其 cardNumber 和 realName 自然保持 NULL。
        return users.stream().map(user -> {
            PgIdCard card = cardsByUser.get(user.getId());
            UserWithIdCardVO vo = new UserWithIdCardVO();
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            if (card != null) {
                vo.setCardNumber(card.getCardNumber());
                vo.setRealName(card.getRealName());
            }
            return vo;
        }).toList();
    }

    @Override
    public List<UserOrderVO> getRightUsersWithOrders() {
        /*
         * 实现步骤：
         * 1. 查询完整用户列表，作为 RIGHT JOIN 的右侧保留集合；
         * 2. 查询全部订单并按 userId 分组；
         * 3. 建立创建时间、订单ID倒序的稳定排序规则；
         * 4. 逐用户输出全部订单，没有订单时补一条空订单结果。
         */

        // 第1步：所有用户都必须出现在最终结果中。
        List<PgUser> users = listUsers();

        // 第2步：预先按 userId 分组，使后续能够直接取得每个用户的订单集合。
        Map<Long, List<PgOrder>> ordersByUser = listOrders().stream()
                .collect(Collectors.groupingBy(PgOrder::getUserId));

        // 第3步：先比较创建时间，再比较主键；NULL 放最后以对齐 SQL 排序语义。
        Comparator<PgOrder> newestFirst = Comparator
                .comparing(PgOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PgOrder::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        // 第4步：用户没有订单时主动补一条空关联结果，体现 RIGHT JOIN 的保留侧语义。
        List<UserOrderVO> result = new ArrayList<>();
        for (PgUser user : users) {
            List<PgOrder> orders = new ArrayList<>(ordersByUser.getOrDefault(user.getId(), List.of()));
            orders.sort(newestFirst);
            if (orders.isEmpty()) {
                result.add(toUserOrder(user, null));
            } else {
                orders.forEach(order -> result.add(toUserOrder(user, order)));
            }
        }
        return result;
    }

    @Override
    public List<ProductOrderAuditVO> getFullProductOrderAudit() {
        /*
         * 实现步骤：
         * 1. 查询 products 和 order_products 两侧全集；
         * 2. 建立商品索引、已引用商品ID集合和结果容器；
         * 3. 遍历全部订单明细，保留匹配数据并标记引用不到商品的孤儿明细；
         * 4. 补充从未被订单明细引用的未售商品；
         * 5. 按商品ID和明细ID排序后返回。
         */

        // 第1步：FULL OUTER JOIN 的两侧都可能产生独立结果，因此必须读取两侧全集。
        List<PgProduct> products = listProducts();
        List<PgOrderProduct> items = listOrderProducts();

        // 第2步：商品索引用于匹配，引用集合用于随后找出 products 侧独有记录。
        Map<Long, PgProduct> productMap = products.stream()
                .collect(Collectors.toMap(PgProduct::getId, Function.identity()));
        Set<Long> referencedProductIds = new HashSet<>();
        List<ProductOrderAuditVO> result = new ArrayList<>();

        // 第3步：先保留 order_products 侧全部行，包括引用不到商品的脏数据。
        for (PgOrderProduct item : items) {
            referencedProductIds.add(item.getProductId());
            PgProduct product = productMap.get(item.getProductId());
            ProductOrderAuditVO vo = new ProductOrderAuditVO();
            vo.setProductId(item.getProductId());
            vo.setProductName(product == null ? null : product.getName());
            vo.setOrderProductId(item.getId());
            vo.setOrderId(item.getOrderId());
            vo.setQuantity(item.getQuantity());
            vo.setMatchStatus(product == null ? "ORPHAN_ORDER_PRODUCT" : "MATCHED");
            result.add(vo);
        }

        // 第4步：再补 products 侧独有记录，完成 FULL OUTER JOIN 的另一半。
        for (PgProduct product : products) {
            if (!referencedProductIds.contains(product.getId())) {
                ProductOrderAuditVO vo = new ProductOrderAuditVO();
                vo.setProductId(product.getId());
                vo.setProductName(product.getName());
                vo.setMatchStatus("UNSOLD_PRODUCT");
                result.add(vo);
            }
        }

        // 第5步：使用 NULL 安全比较器形成与 SQL ORDER BY 一致的稳定顺序。
        result.sort(Comparator.comparing(ProductOrderAuditVO::getProductId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProductOrderAuditVO::getOrderProductId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Override
    public List<UserProductCandidateVO> getCrossUserProductCandidates(int limit) {
        /*
         * 实现步骤：
         * 1. 校验结果数量上限；
         * 2. 初始化结果并查询完整商品列表；
         * 3. 遍历完整用户列表与商品列表，生成笛卡尔积候选；
         * 4. 达到 limit 时立即停止，否则返回全部已生成结果。
         */

        // 第1步：CROSS JOIN 的结果量是两侧行数乘积，必须先限制最大返回数量。
        validateLimit(limit);

        // 第2步：商品列表只查询一次，在所有用户循环中复用。
        List<UserProductCandidateVO> result = new ArrayList<>();
        List<PgProduct> products = listProducts();

        // 第3步：两层循环对应 SQL 的用户 × 商品笛卡尔积。
        for (PgUser user : listUsers()) {
            for (PgProduct product : products) {
                // 第4步：在添加下一条记录前检查上限，防止继续构造无用对象。
                if (result.size() >= limit) {
                    return result;
                }
                UserProductCandidateVO vo = new UserProductCandidateVO();
                vo.setUserId(user.getId());
                vo.setUsername(user.getUsername());
                vo.setProductId(product.getId());
                vo.setProductName(product.getName());
                vo.setProductPrice(product.getPrice());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public List<UserOrderVO> getLatestOrderPerUser() {
        /*
         * 实现步骤：
         * 1. 建立按创建时间、订单ID选择最新订单的比较规则；
         * 2. 查询全部订单并按 userId 合并，每组只保留最新一笔；
         * 3. 遍历全部用户并关联最新订单，无订单用户仍保留。
         */

        // 第1步：较新的创建时间胜出；时间相同时由更大的订单ID打破平局。
        Comparator<PgOrder> latestComparator = Comparator
                .comparing(PgOrder::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(PgOrder::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

        // 第2步：toMap 的冲突合并函数相当于为每个用户执行 ORDER BY ... LIMIT 1。
        Map<Long, PgOrder> latestByUser = listOrders().stream().collect(Collectors.toMap(
                PgOrder::getUserId,
                Function.identity(),
                (left, right) -> latestComparator.compare(left, right) >= 0 ? left : right));

        // 第3步：遍历用户全集保持 LEFT JOIN 语义，Map 中不存在订单时关联字段为 NULL。
        // 一次读取全部订单后分组，避免对每个用户分别 select limit 1 形成 N+1。
        return listUsers().stream()
                .map(user -> toUserOrder(user, latestByUser.get(user.getId())))
                .toList();
    }

    // ==================== 聚合、子查询和函数的 Java 等价实现 ====================

    @Override
    public List<UserOrderStatVO> getUserOrderStats() {
        /*
         * 实现步骤：
         * 1. 查询全部订单并按 userId 分组；
         * 2. 遍历完整用户列表，为每个用户计算订单数和消费金额；
         * 3. 按消费金额和用户ID形成稳定排序。
         */

        // 第1步：订单分组相当于为后续每个用户准备其聚合输入集合。
        Map<Long, List<PgOrder>> ordersByUser = listOrders().stream()
                .collect(Collectors.groupingBy(PgOrder::getUserId));

        // 第2步：从用户全集出发，确保无订单用户也能得到计数0、金额0的统计结果。
        return listUsers().stream()
                .map(user -> toUserOrderStat(user, ordersByUser.getOrDefault(user.getId(), List.of())))
                // 第3步：复用统一比较器，保持消费金额降序和用户ID升序。
                .sorted(userStatComparator())
                .toList();
    }

    @Override
    public List<UserOrderStatVO> getTopSpendingUsers(int limit) {
        /*
         * 实现步骤：
         * 1. 校验最多返回数量；
         * 2. 复用用户订单统计，取得已经按消费金额排序的结果；
         * 3. 排除无订单用户并截取前 limit 条。
         */

        // 第1步：限制查询结果规模，拒绝0、负数和过大的 limit。
        validateLimit(limit);

        // 第2步：getUserOrderStats 已完成分组聚合和消费金额排序。
        return getUserOrderStats().stream()
                // 第3步：消费榜只保留真实下过订单的用户，然后取前N名。
                .filter(stat -> stat.getOrderCount() > 0)
                .limit(limit)
                .toList();
    }

    @Override
    public List<UserSimpleVO> getUsersWithOrders() {
        /*
         * 实现步骤：
         * 1. 查询订单并提取去重后的用户ID集合；
         * 2. 没有任何订单时提前返回空结果；
         * 3. 使用用户ID集合执行 IN 查询并转换为简单用户对象。
         */

        // 第1步：Set 同时完成用户ID提取和去重，对应 EXISTS 只关心是否至少存在一行。
        Set<Long> userIds = listOrders().stream().map(PgOrder::getUserId).collect(Collectors.toSet());

        // 第2步：空集合不下发 IN ()，直接返回符合语义的空列表。
        if (userIds.isEmpty()) {
            return List.of();
        }

        // 第3步：一次 IN 查询取得全部命中用户，避免按用户逐个检查订单形成 N+1。
        return userMapper.selectList(Wrappers.<PgUser>lambdaQuery()
                        .in(PgUser::getId, userIds)
                        .orderByAsc(PgUser::getId))
                .stream().map(this::toUserSimple).toList();
    }

    @Override
    public List<UserSimpleVO> getUsersWithoutOrders() {
        /*
         * 实现步骤：
         * 1. 查询订单并提取所有已下单用户ID；
         * 2. 遍历完整用户列表，保留ID不在订单用户集合中的用户；
         * 3. 转换为简单用户对象。
         */

        // 第1步：该 Set 表示 NOT EXISTS 判断中需要排除的用户集合。
        Set<Long> userIds = listOrders().stream().map(PgOrder::getUserId).collect(Collectors.toSet());

        // 第2步：从用户全集做差集，订单集合为空时自然保留全部用户。
        return listUsers().stream()
                .filter(user -> !userIds.contains(user.getId()))
                // 第3步：筛选完成后再转换，避免为最终会被排除的用户创建 VO。
                .map(this::toUserSimple)
                .toList();
    }

    @Override
    public List<UserOrderStatVO> getUsersByOrderCount(int minOrderCount) {
        /*
         * 实现步骤：
         * 1. 校验最小订单数不能为负数；
         * 2. 复用用户订单统计并保留订单数达到下界的用户；
         * 3. 按订单数倒序、用户ID升序重新排序。
         */

        // 第1步：HAVING 下界允许为0，但不能是负数。
        BusinessAssert.isTrue(minOrderCount >= 0, "最小订单数不能为负数");

        // 第2步：在聚合结果上过滤，等价于 SQL GROUP BY 后的 HAVING COUNT(...) >= 条件。
        return getUserOrderStats().stream()
                .filter(stat -> stat.getOrderCount() >= minOrderCount)
                // 第3步：该接口按订单数而不是消费金额排序，因此需要覆盖统计方法原有顺序。
                .sorted(Comparator.comparing(UserOrderStatVO::getOrderCount).reversed()
                        .thenComparing(UserOrderStatVO::getUserId))
                .toList();
    }

    @Override
    public List<UserSimpleVO> getUsersUnionAll() {
        /*
         * 实现步骤：
         * 1. 查询状态为 ACTIVE 的用户；
         * 2. 查询状态非 ACTIVE 或状态为 NULL 的用户；
         * 3. 按 UNION ALL 语义直接拼接两组结果，不执行去重；
         * 4. 按用户ID排序并转换为简单用户对象。
         */

        // 第1步：取得 UNION ALL 的第一组结果。
        List<PgUser> active = userMapper.selectList(Wrappers.<PgUser>lambdaQuery()
                .eq(PgUser::getStatus, "ACTIVE"));

        // 第2步：第二组与第一组互斥，同时显式包含状态为 NULL 的用户。
        List<PgUser> other = userMapper.selectList(Wrappers.<PgUser>lambdaQuery()
                .ne(PgUser::getStatus, "ACTIVE")
                .or()
                .isNull(PgUser::getStatus));

        // 第3步：addAll 保留两组的全部行，不像 Set 那样执行去重。
        List<PgUser> unionAll = new ArrayList<>(active.size() + other.size());
        unionAll.addAll(active);
        unionAll.addAll(other);

        // 第4步：拼接后统一排序并转换，保证两套接口返回顺序一致。
        return unionAll.stream().sorted(Comparator.comparing(PgUser::getId))
                .map(this::toUserSimple).toList();
    }

    @Override
    public List<OrderStatusVO> getOrderStatusWithName() {
        /*
         * 实现步骤：
         * 1. 查询全部订单；
         * 2. 对每笔订单调用手工 statusName 映射状态文本；
         * 3. 将状态码和状态名称平铺到 OrderStatusVO。
         */

        // 第1步：listOrders 提供按订单ID稳定排序的原始字符串状态数据。
        return listOrders().stream().map(order -> {
            // 第2步：statusName 通过 Java switch 把数据库状态码转换为中文展示文本。
            OrderStatusVO vo = new OrderStatusVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStatus(order.getStatus());
            vo.setStatusName(statusName(order.getStatus()));

            // 第3步：响应保持 status/statusName 两个扁平字段，不暴露内部转换过程。
            return vo;
        }).toList();
    }

    @Override
    public List<OrderStatusVO> getOrderStatusWithEnumMapping() {
        /*
         * 实现步骤：
         * 1. 查询只包含订单ID、订单号和枚举状态的专用投影；
         * 2. 对每条结果处理 NULL 状态，或从枚举读取 code/text；
         * 3. 组装 status/statusName 平铺响应。
         */

        // 第1步：查询专用投影；数据库字符串在结果映射阶段已经自动转换为枚举。
        // 专用投影实体的 status 字段是 OrderStatusEnum。BaseMapper 查询结果赋值时，
        // MyBatis-Plus 会根据枚举 code 上的 @EnumValue 自动完成字符串到枚举的转换。
        List<PgOrderStatusEnumDemo> orders = orderStatusEnumMapper.selectList(
                Wrappers.<PgOrderStatusEnumDemo>lambdaQuery()
                        // 只查询接口需要的三个字段，避免为了枚举演示传输无关的金额和时间列。
                        .select(PgOrderStatusEnumDemo::getId,
                                PgOrderStatusEnumDemo::getOrderNo,
                                PgOrderStatusEnumDemo::getStatus)
                        .orderByAsc(PgOrderStatusEnumDemo::getId));

        // 第2步：枚举为空时保持数据库NULL语义，否则分别读取持久化code和展示text。
        return orders.stream().map(order -> {
            OrderStatusVO vo = new OrderStatusVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());

            if (order.getStatus() == null) {
                vo.setStatus(null);
                vo.setStatusName("未知");
            } else {
                vo.setStatus(order.getStatus().getCode());
                vo.setStatusName(order.getStatus().getText());
            }

            // 第3步：枚举只存在于持久层投影中，对外仍返回两个字符串字段。
            return vo;
        }).toList();
    }

    @Override
    public List<UserContactVO> getUsersWithCoalescePhone() {
        /*
         * 实现步骤：
         * 1. 查询全部用户；
         * 2. 将 NULL 手机号替换为“未填写”；
         * 3. 组装用户联系方式响应。
         */

        // 第1步：取得统一排序的用户数据。
        return listUsers().stream().map(user -> {
            UserContactVO vo = new UserContactVO();
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());

            // 第2步：三元表达式在 Java 中复现 SQL COALESCE(phone, '未填写')。
            vo.setPhone(user.getPhone() == null ? "未填写" : user.getPhone());

            // 第3步：返回转换后的联系方式对象。
            return vo;
        }).toList();
    }

    @Override
    public List<UserDateStatVO> getUserDateStats() {
        /*
         * 实现步骤：
         * 1. 查询全部用户；
         * 2. 保留原始创建时间，并在非空时提取日期和年份；
         * 3. 组装日期统计响应。
         */

        // 第1步：取得每个用户的原始创建时间。
        return listUsers().stream().map(user -> {
            UserDateStatVO vo = new UserDateStatVO();
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setCreatedAt(user.getCreatedAt());

            // 第2步：非空判断对应 SQL 日期函数面对 NULL 时不产生有效日期结果。
            if (user.getCreatedAt() != null) {
                vo.setCreateDate(user.getCreatedAt().toLocalDate());
                vo.setCreateYear(user.getCreatedAt().getYear());
            }

            // 第3步：同时返回原始时间、日期部分和年份。
            return vo;
        }).toList();
    }

    @Override
    public List<UserRankVO> getUserSpendingRank() {
        /*
         * 实现步骤：
         * 1. 取得按消费金额排序的用户统计，并排除无订单用户；
         * 2. 按当前顺序从1开始生成连续排名；
         * 3. 返回完整排名列表。
         */

        // 第1步：getUserOrderStats 已按消费金额倒序，过滤后仍保持该顺序。
        List<UserOrderStatVO> stats = getUserOrderStats().stream()
                .filter(stat -> stat.getOrderCount() > 0)
                .toList();

        // 第2步：数组下标从0开始，因此排名使用 index + 1。
        List<UserRankVO> result = new ArrayList<>(stats.size());
        for (int index = 0; index < stats.size(); index++) {
            UserOrderStatVO stat = stats.get(index);
            UserRankVO vo = new UserRankVO();
            vo.setUserId(stat.getUserId());
            vo.setUsername(stat.getUsername());
            vo.setTotalSpent(stat.getTotalSpent());
            vo.setRank((long) index + 1);
            result.add(vo);
        }

        // 第3步：结果顺序即排名顺序，不再执行额外排序。
        return result;
    }

    @Override
    public List<UserSpendingLevelVO> getUserSpendingLevels() {
        /*
         * 实现步骤：
         * 1. 取得所有用户的订单统计；
         * 2. 根据总消费金额计算 HIGH、MEDIUM、LOW 等级；
         * 3. 组装包含统计值和消费等级的响应。
         */

        // 第1步：统一统计结果已经包含订单数、总消费和稳定排序。
        return getUserOrderStats().stream().map(stat -> {
            UserSpendingLevelVO vo = new UserSpendingLevelVO();
            vo.setUserId(stat.getUserId());
            vo.setUsername(stat.getUsername());
            vo.setOrderCount(stat.getOrderCount());
            vo.setTotalSpent(stat.getTotalSpent());

            // 第2步：spendingLevel 集中维护金额区间到等级文本的映射。
            vo.setSpendingLevel(spendingLevel(stat.getTotalSpent()));

            // 第3步：返回分层后的用户统计对象。
            return vo;
        }).toList();
    }

    @Override
    public List<ProductSalesStatVO> getProductSalesStats() {
        /*
         * 实现步骤：
         * 1. 查询全部订单明细并按 productId 分组；
         * 2. 遍历完整商品列表，为每个商品取得对应明细集合；
         * 3. 分别计算销售数量和销售金额并组装统计对象；
         * 4. 按销售额倒序、商品ID升序返回。
         */

        // 第1步：明细分组为每个商品准备聚合输入，无明细商品稍后使用空集合。
        Map<Long, List<PgOrderProduct>> itemsByProduct = listOrderProducts().stream()
                .collect(Collectors.groupingBy(PgOrderProduct::getProductId));

        // 第2步：从完整商品列表出发，确保未售商品也得到数量0、销售额0的结果。
        return listProducts().stream().map(product -> {
            List<PgOrderProduct> items = itemsByProduct.getOrDefault(product.getId(), List.of());

            // 第3步：数量求和忽略NULL；销售额按成交单价快照 × 数量后累加。
            long totalQuantity = items.stream().map(PgOrderProduct::getQuantity)
                    .filter(quantity -> quantity != null).mapToLong(Integer::longValue).sum();
            BigDecimal totalSales = items.stream()
                    .filter(item -> item.getQuantity() != null && item.getUnitPrice() != null)
                    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            ProductSalesStatVO vo = new ProductSalesStatVO();
            vo.setProductId(product.getId());
            vo.setProductName(product.getName());
            vo.setTotalQuantity(totalQuantity);
            vo.setTotalSales(totalSales);
            vo.setStock(product.getStock());
            return vo;
        // 第4步：销售额相同时使用商品ID打破平局，保证顺序稳定。
        }).sorted(Comparator.comparing(ProductSalesStatVO::getTotalSales).reversed()
                .thenComparing(ProductSalesStatVO::getProductId)).toList();
    }

    // ==================== 跨表事务 ====================

    /**
     * 使用 MyBatis-Plus 官方 BaseMapper 和 Lambda Wrapper 完成一次完整下单。
     *
     * <p>业务步骤与普通 MyBatis 实现保持一致，区别只在数据访问方式：商品通过 selectBatchIds
     * 批量加载，订单和明细通过 BaseMapper.insert 写入，库存通过 LambdaUpdateWrapper 生成带
     * stock >= quantity 条件的原子 UPDATE。这样可以直接对照 XML SQL 与 Plus API 的写法。</p>
     *
     * <p>整个方法由 playgroundTransactionManager 管理。任何 BusinessAssert 校验失败都会抛出运行时
     * BusinessException；数据库异常也会继续向外抛出，因此订单、明细和库存不会只提交一部分。</p>
     */
    @Override
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public CompleteOrderResponse createCompleteOrder(CompleteOrderCreateRequest request) g{
        /*
         * 实现步骤：
         * 1. 校验请求结构并确认下单用户存在；
         * 2. 合并请求中重复商品的购买数量；
         * 3. 批量查询全部商品并确认商品完整存在；
         * 4. 使用数据库价格、库存计算可信订单金额；
         * 5. 构造并写入订单头；
         * 6. 构造订单明细价格快照，并按商品ID排序；
         * 7. 按固定顺序逐项写入明细并执行带库存条件的原子扣减；
         * 8. 全部操作成功后组装响应，由事务统一提交。
         */

        // 第1步：校验订单号、用户ID、商品列表和正数数量，再确认下单用户存在。
        // 结构校验放在最前面，可以避免非法请求进入后续多表写入流程。
        validateCompleteOrderRequest(request);
        BusinessAssert.notNull(userMapper.selectById(request.getUserId()), "下单用户不存在");

        // 第2步：合并重复商品。例如同一商品传入数量2和3，最终按数量5处理。
        // 这既符合 order_products 的联合唯一约束，也避免同一个商品被多次扣库存。
        Map<Long, Integer> quantities = mergeQuantities(request.getItems());

        // 第3步：一次批量查询全部商品，并通过结果数量确认请求中的商品全部存在。
        // selectBatchIds 会生成一次主键 IN 查询，比循环调用 selectById 更能避免 N+1。
        // 转成 Map 后可快速按 productId 取出商品，并能检查请求中的商品是否全部存在。
        Map<Long, PgProduct> productMap = productMapper.selectBatchIds(quantities.keySet()).stream()
                .collect(Collectors.toMap(PgProduct::getId, Function.identity()));
        BusinessAssert.isTrue(productMap.size() == quantities.size(), "订单中包含不存在的商品");

        // 第4步：使用数据库商品价格计算订单金额，拒绝客户端自报价格。
        // 这里的库存检查用于提供具体商品名称；并发安全仍由后面的条件 UPDATE 保证。
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            PgProduct product = productMap.get(entry.getKey());
            BusinessAssert.isTrue(product.getStock() != null && product.getStock() >= entry.getValue(),
                    "商品库存不足: " + product.getName());
            BusinessAssert.isTrue(product.getPrice() != null, "商品价格不能为空: " + product.getName());
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        // 第5步：构造并写入订单头。BaseMapper.insert 成功后会把数据库生成的主键回填到 order.id。
        // PENDING 是新订单的初始状态；totalAmount 是上一步在服务端计算出的可信金额。
        PgOrder order = new PgOrder();
        order.setUserId(request.getUserId());
        order.setOrderNo(request.getOrderNo());
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());

        // 订单号依赖数据库唯一约束保证最终一致性。重复订单号会转换为 BusinessException，
        // 运行时异常离开该方法后会触发当前事务回滚。
        BusinessAssert.isTrue(executeUniqueWrite(() -> orderMapper.insert(order), "订单号已存在") == 1, "订单创建失败");

        // 第6步：先构造全部订单明细并按商品ID排序。unitPrice 是成交时的价格快照，
        // 不能在查询历史订单时改用以后可能已经变化的 products.price。
        List<PgOrderProduct> orderProducts = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            PgProduct product = productMap.get(entry.getKey());
            PgOrderProduct item = new PgOrderProduct();
            item.setOrderId(order.getId());
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

        // 第7步：按照固定顺序逐个写入订单明细，并紧接着扣减对应商品库存。
        for (PgOrderProduct item : orderProducts) {
            BusinessAssert.isTrue(executeUniqueWrite(() -> orderProductMapper.insert(item),
                    "同一订单中不能重复添加相同商品") == 1, "订单明细写入不完整");

            // LambdaUpdateWrapper 最终生成的核心 SQL 语义为：
            // UPDATE products SET stock = stock - quantity
            // WHERE id = productId AND stock >= quantity
            // ge 负责把库存充足条件放入 WHERE，setDecrBy 负责生成 stock = stock - quantity。
            // 两者位于同一条 UPDATE 中，避免“先查库存、后扣库存”之间出现并发超卖窗口。
            LambdaUpdateWrapper<PgProduct> stockUpdate = Wrappers.lambdaUpdate(PgProduct.class)
                    .eq(PgProduct::getId, item.getProductId())
                    .ge(PgProduct::getStock, item.getQuantity())
                    .setDecrBy(true, PgProduct::getStock, item.getQuantity());

            // 影响一行才表示扣减成功。返回 0 时可能是商品被删除，或者其他事务先消耗了库存；
            // 此处抛异常后，当前循环已经插入的所有明细和订单头都会一起回滚。
            BusinessAssert.isTrue(productMapper.update(null, stockUpdate) == 1, "商品库存已发生变化，请重试");
        }

        // 第8步：全部数据库操作成功后组装响应，方法正常结束时事务才会提交。
        // itemCount 代表购买总件数；不同商品种类数则是 quantities.size()。
        CompleteOrderResponse response = new CompleteOrderResponse();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setTotalAmount(totalAmount);
        response.setItemCount(sumItemCount(quantities));
        return response;
    }

    // ==================== VO 组装与公共校验 ====================

    private OrderDetailVO toOrderDetail(PgOrder order, PgUser user, PgOrderProduct item, PgProduct product) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUsername(user.getUsername());
        vo.setProductName(product.getName());
        vo.setQuantity(item.getQuantity());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        return vo;
    }

    private UserOrderVO toUserOrder(PgUser user, PgOrder order) {
        UserOrderVO vo = new UserOrderVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setOrderTime(order.getCreatedAt());
        }
        return vo;
    }

    private UserOrderStatVO toUserOrderStat(PgUser user, List<PgOrder> orders) {
        UserOrderStatVO vo = new UserOrderStatVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setOrderCount((long) orders.size());
        vo.setTotalSpent(orders.stream().map(PgOrder::getTotalAmount)
                .filter(amount -> amount != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return vo;
    }

    private Comparator<UserOrderStatVO> userStatComparator() {
        return Comparator.comparing(UserOrderStatVO::getTotalSpent).reversed()
                .thenComparing(UserOrderStatVO::getUserId);
    }

    private UserSimpleVO toUserSimple(PgUser user) {
        UserSimpleVO vo = new UserSimpleVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        return vo;
    }

    private String statusName(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PAID" -> "已支付";
            case "PENDING" -> "待支付";
            case "CANCELLED" -> "已取消";
            default -> "未知";
        };
    }

    private String spendingLevel(BigDecimal totalSpent) {
        if (totalSpent.compareTo(new BigDecimal("20000")) >= 0) {
            return "HIGH";
        }
        if (totalSpent.compareTo(new BigDecimal("10000")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

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
     * Wrapper 不会替 Service 判断范围是否合法，因此两套实现必须在构造查询前执行同一规则。
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

    /** 商品查询统一拒绝负价格、反向价格区间和负库存下界。 */
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

    private <T> PageResult<T> pageResult(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setList(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        return result;
    }

}
