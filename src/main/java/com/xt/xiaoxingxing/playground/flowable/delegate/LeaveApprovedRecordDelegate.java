package com.xt.xiaoxingxing.playground.flowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** 通过分支子流程中的学习节点：记录后处理已经执行。 */
@Component("leaveApprovedRecordDelegate")
public class LeaveApprovedRecordDelegate implements JavaDelegate {

    public static final String VARIABLE_NAME = "approvedRecorded";

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(VARIABLE_NAME, true);
    }
}
