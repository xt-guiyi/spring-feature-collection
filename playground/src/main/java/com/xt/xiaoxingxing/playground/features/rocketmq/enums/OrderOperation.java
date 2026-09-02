package com.xt.xiaoxingxing.playground.features.rocketmq.enums;

import com.xt.xiaoxingxing.playground.features.rocketmq.constants.OrderMqConstants;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;

/** 订单操作类型。 */
public enum OrderOperation {

    CREATE(OrderMqConstants.TAG_ORDER_CREATED),
    PAY(OrderMqConstants.TAG_ORDER_PAID),
    CANCEL(OrderMqConstants.TAG_ORDER_CANCELLED);

    private final String tag;

    OrderOperation(String tag) {
        this.tag = tag;
    }

    /** 获取消息标签。 */
    public String tag() {
        return tag;
    }

    /** 根据消息标签获取订单操作类型。 */
    public static OrderOperation fromTag(String tag) {
        for (OrderOperation operation : values()) {
            if (operation.tag.equals(tag)) {
                return operation;
            }
        }
        throw new BusinessException("不支持的订单消息Tag: " + tag);
    }
}
