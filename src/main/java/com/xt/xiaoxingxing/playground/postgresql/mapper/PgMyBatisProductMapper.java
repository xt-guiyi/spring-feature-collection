package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.ProductQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * products 表的普通 MyBatis Mapper，包含事务下单需要的原子库存扣减。
 *
 * <p>普通查询展示 XML 动态条件；decrementStock 则展示把业务前置条件写入
 * UPDATE WHERE，从数据库层面完成并发安全的“检查并扣减”。</p>
 */
@Mapper
public interface PgMyBatisProductMapper {

    /** 插入商品并通过 RETURNING 返回主键。 */
    Long insertProduct(PgProduct product);

    PgProduct selectProductById(@Param("id") Long id);

    List<PgProduct> selectAllProducts();

    /** 一次 IN 查询批量读取下单所需商品，避免 N+1。 */
    List<PgProduct> selectProductsByIds(@Param("ids") List<Long> ids);

    /** 演示 LIKE、BETWEEN、单边范围和库存下界的动态组合。 */
    List<PgProduct> selectProductsByCondition(ProductQueryRequest request);

    int updateProduct(PgProduct product);

    int deleteProductById(@Param("id") Long id);

    /** 仅当当前库存充足时原子扣减，成功时影响一行。 */
    int decrementStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
