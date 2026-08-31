package com.xt.xiaoxingxing.playground.flowable.vo;

import lombok.Data;

/** DMN 请假路由执行结果。 */
@Data
public class LeaveRouteVO {

    private Integer leaveDays;
    private String leaveType;
    private String approvalRoute;
    private String requiredRoles;
    private Integer requiredApprovalCount;
}
