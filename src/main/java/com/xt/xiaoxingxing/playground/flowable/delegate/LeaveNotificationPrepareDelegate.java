package com.xt.xiaoxingxing.playground.flowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** 通过分支子流程中的学习节点：只记录通知准备状态，不发送外部消息。 */
@Component("leaveNotificationPrepareDelegate")
public class LeaveNotificationPrepareDelegate implements JavaDelegate {

    public static final String VARIABLE_NAME = "notificationPrepared";

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(VARIABLE_NAME, true);
    }
}
