package com.xt.xiaoxingxing.shared.core.validation;

import com.xt.xiaoxingxing.shared.core.exception.BusinessException;

/**
 * 业务层通用断言工具。
 */
public final class BusinessAssert {

    private BusinessAssert() {
        // 工具类只提供静态方法，禁止创建无意义的实例。
    }

    /** 条件必须成立，否则使用指定消息抛出业务异常。 */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(message);
        }
    }

    /**
     * 对象必须存在，并返回原对象。
     *
     * <p>返回值设计可以直接用于赋值，例如：
     * {@code PgUser user = BusinessAssert.notNull(mapper.selectById(id), "用户不存在");}</p>
     */
    public static <T> T notNull(T value, String message) {
        isTrue(value != null, message);
        return value;
    }

    /** 判断字符串是否包含至少一个非空白字符，可用于动态 SQL 条件。 */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 字符串必须包含至少一个非空白字符。 */
    public static void hasText(String value, String message) {
        isTrue(hasText(value), message);
    }

    /**
     * 数据库写操作必须至少影响一行。
     *
     * @return 校验通过后固定返回 {@code true}，便于 CRUD Service 直接作为 boolean 结果返回
     */
    public static boolean affected(int affectedRows, String message) {
        isTrue(affectedRows > 0, message);
        return true;
    }
}
