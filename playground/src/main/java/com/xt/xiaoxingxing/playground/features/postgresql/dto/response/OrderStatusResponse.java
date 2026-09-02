package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

/**
 * 订单状态学习接口的统一响应对象。
 *
 * <p>无论底层采用 SQL {@code CASE WHEN}、Java {@code switch}，还是枚举自动映射，
 * Controller 最终都返回同一组平铺字段。这样可以只比较持久层实现差异，不让响应结构
 * 的变化干扰学习。</p>
 */
@Data
public class OrderStatusResponse {

    /** 订单主键。 */
    private Long orderId;

    /** 业务订单号。 */
    private String orderNo;

    /** 数据库存储的稳定状态码，例如 PAID。 */
    private String status;

    /** 根据状态码得到的展示名称，例如“已支付”。 */
    private String statusName;
}
