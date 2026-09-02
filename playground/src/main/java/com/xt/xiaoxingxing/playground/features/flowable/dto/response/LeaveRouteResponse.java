package com.xt.xiaoxingxing.playground.features.flowable.dto.response;

import lombok.Data;

/** DMN 请假路由执行结果。 */
@Data
public class LeaveRouteResponse {

    private Integer leaveDays;
    private String leaveType;
    private String approvalRoute;
    private String requiredRoles;
    private Integer requiredApprovalCount;
}
