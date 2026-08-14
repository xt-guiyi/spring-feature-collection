package com.xt.xiaoxingxing.playground.xxljob.dto.request;

/**
 * 基础 Handler 的三种可观察结果。
 *
 * <p>它们分别演示“显式成功”“业务主动标记失败”和“异常冒泡后由执行线程判失败”，
 * 不能把后两种都写成普通返回，否则调度中心会把失败误判为成功。</p>
 */
public enum BasicJobOutcome {
    SUCCESS,
    FAIL,
    EXCEPTION
}
