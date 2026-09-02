package com.xt.xiaoxingxing.playground.features.postgresql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xt.xiaoxingxing.playground.features.postgresql.enums.OrderStatusEnum;
import lombok.Data;

/**
 * orders 表的枚举自动映射专用投影模型。
 *
 * <p>现有 {@link PgOrder} 继续使用 String status，以保留 SQL CASE WHEN 与 Java switch
 * 的原始学习案例。本类只选择演示接口需要的三个字段，并把 status 声明为
 * {@link OrderStatusEnum}，从而单独观察数据库状态码到 Java 枚举的自动转换过程。</p>
 *
 * <p>同一张表映射两个 Java 类型在这里是有意的教学设计：它隔离了枚举案例，避免为了一个
 * 演示接口修改现有 CRUD、动态查询和事务代码。该类型当前只用于读取，不作为 Controller
 * 的直接响应对象。</p>
 */
@Data
@TableName("orders")
public class PgOrderStatusEnumDemo {

    /** 订单主键，用于稳定排序以及填充响应的 orderId。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号；MyBatis-Plus 会把 orderNo 转换为数据库列 order_no。 */
    private String orderNo;

    /** 数据库 status 字符串由枚举类型处理器自动转换为 OrderStatusEnum。 */
    private OrderStatusEnum status;
}
