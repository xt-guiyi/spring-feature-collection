package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.xt.xiaoxingxing.playground.postgresql.entity.PgIdCard;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 关系表的普通 MyBatis Mapper。
 *
 * <p>id_cards 表表达用户一对一关系，order_products 表表达订单与商品的多对多关系。</p>
 */
@Mapper
public interface PgMyBatisRelationMapper {

    /** 创建用户身份证关系并返回主键。 */
    Long insertIdCard(PgIdCard idCard);

    /** 按关系表主键查询身份证记录。 */
    PgIdCard selectIdCardById(@Param("id") Long id);

    /** 查询全部身份证记录并保持固定顺序。 */
    List<PgIdCard> selectAllIdCards();

    /** 选择性更新身份证关系中的非空字段。 */
    int updateIdCard(PgIdCard idCard);

    /** 按关系表主键删除身份证记录。 */
    int deleteIdCardById(@Param("id") Long id);

    /** 创建订单商品关系并返回主键。 */
    Long insertOrderProduct(PgOrderProduct orderProduct);

    /** 按中间表主键查询订单商品关系。 */
    PgOrderProduct selectOrderProductById(@Param("id") Long id);

    /** 查询全部订单商品关系并保持固定顺序。 */
    List<PgOrderProduct> selectAllOrderProducts();

    /** 选择性更新关系、数量或成交单价。 */
    int updateOrderProduct(PgOrderProduct orderProduct);

    /** 按中间表主键删除订单商品关系。 */
    int deleteOrderProductById(@Param("id") Long id);

    /** 用单条 INSERT 的多组 VALUES 批量写入完整订单的所有明细。 */
    int batchInsertOrderProducts(@Param("items") List<PgOrderProduct> items);
}
