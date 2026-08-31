package com.xt.xiaoxingxing.playground.flowable.support;

import com.xt.xiaoxingxing.playground.flowable.enums.ApprovalRoute;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import org.springframework.stereotype.Component;

/**
 * 请假学习案例的服务端审批人解析器。
 *
 * <p>当前仓库的 {@code users} 表只有用户基础资料，没有组织架构关系，
 * 因此先固定经理、HR 和负责人的用户 ID。流程内 Delegate 会校验当前路线需要的用户
 * 是否存在且处于 ACTIVE 状态；以后接入组织架构服务时，只需要替换本类的解析逻辑，
 * 对外的 StartLeaveRequest 不需要暴露审批人字段。</p>
 */
@Component
public class FlowableApprovalUserResolver {

    /** 学习数据中的经理用户 ID；后续接入组织架构服务时替换本映射。 */
    public static final long MANAGER_ID = 2L;

    /** 学习数据中的 HR 用户 ID；后续接入组织架构服务时替换本映射。 */
    public static final long HR_ID = 1L;

    /** 学习数据中的负责人用户 ID；需要先在 users 表准备 ACTIVE 用户 3。 */
    public static final long LEADER_ID = 3L;

    /** 按 DMN 路线返回本次流程实际需要的服务端审批人。 */
    public ResolvedApprovers resolve(ApprovalRoute route) {
        BusinessAssert.notNull(route, "审批路线不能为空");
        return switch (route) {
            case MANAGER -> new ResolvedApprovers(MANAGER_ID, null, null);
            case MANAGER_HR -> new ResolvedApprovers(MANAGER_ID, HR_ID, null);
            case MANAGER_HR_LEADER -> new ResolvedApprovers(MANAGER_ID, HR_ID, LEADER_ID);
        };
    }

    /** 服务端解析出的、与当前路线对应的审批人集合。 */
    public record ResolvedApprovers(Long managerId, Long hrId, Long leaderId) {
    }
}
