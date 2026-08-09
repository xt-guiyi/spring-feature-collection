package com.xt.xiaoxingxing.playground.postgresql.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 订单状态枚举自动映射学习案例。
 *
 * <p>数据库的 {@code orders.status} 列保存 {@code PENDING}、{@code PAID}、
 * {@code CANCELLED} 等稳定状态码；Java 代码则使用枚举同时管理状态码和中文名称，
 * 避免在多个 Service 中重复编写 {@code switch}。</p>
 *
 * <p>{@link EnumValue} 标记的 {@link #code} 是真正写入数据库、并用于从数据库反向
 * 查找枚举常量的值。{@link #text} 只负责接口展示，不会被保存到 status 列。</p>
 *
 * <p>如果数据库出现枚举中未声明的非空 code，类型处理器会在构造查询结果时转换失败，
 * Service 无法把它当作“未知”继续返回。生产项目通常还应使用数据库 CHECK 约束或状态字典
 * 保证状态码合法；本案例不修改现有表结构。</p>
 */
@Getter
public enum OrderStatusEnum {

    PENDING("PENDING", "待支付"),
    PAID("PAID", "已支付"),
    CANCELLED("CANCELLED", "已取消");

    /**
     * 数据库存储值。
     *
     * <p>MyBatis-Plus 的枚举类型处理器看到 {@code @EnumValue} 后，会完成：</p>
     * <ul>
     *     <li>写入：{@code OrderStatusEnum.PAID -> "PAID"}</li>
     *     <li>读取：{@code "PAID" -> OrderStatusEnum.PAID}</li>
     * </ul>
     */
    @EnumValue
    private final String code;

    /** 展示名称，只由 Service 读取并填入响应的平铺 statusName 字段。 */
    private final String text;

    OrderStatusEnum(String code, String text) {
        this.code = code;
        this.text = text;
    }
}
