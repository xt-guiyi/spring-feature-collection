package com.xt.xiaoxingxing.playground.features.flowable.constants;

/**
 * Flowable 模块使用的流程、决策、变量和任务定义名称。
 *
 * <p>这些值是 BPMN/DMN 资源与 Java 代码之间的稳定契约，不能在业务代码中散落字符串。
 * 运行参数（数据源、建表策略等）仍由 Flowable 官方配置负责。</p>
 */
public final class FlowableNames {

    /** 请假审批 BPMN 流程定义的 key。 */
    public static final String PROCESS_DEFINITION_KEY = "leaveApprovalProcess";
    /** 请假审批 DMN 决策表的 key。 */
    public static final String DECISION_KEY = "leaveApprovalRoute";

    /** 业务申请表主键，用于关联 Flowable 流程实例和业务记录。 */
    public static final String VAR_LEAVE_REQUEST_ID = "leaveRequestId";
    /** 当前流程路线解析出的经理用户 ID。 */
    public static final String VAR_MANAGER_ID = "managerId";
    /** 当前流程路线解析出的 HR 用户 ID。 */
    public static final String VAR_HR_ID = "hrId";
    /** 当前流程路线解析出的负责人用户 ID。 */
    public static final String VAR_LEADER_ID = "leaderId";
    /** 请假天数，也是 DMN 决策表的主要输入。 */
    public static final String VAR_LEAVE_DAYS = "leaveDays";
    /** 请假类型，作为 DMN 输入的一部分保存。 */
    public static final String VAR_LEAVE_TYPE = "leaveType";
    /** DMN 返回的审批路线，例如 MANAGER_HR。 */
    public static final String VAR_APPROVAL_ROUTE = "approvalRoute";
    /** 并行多实例节点要遍历的审批人用户 ID 集合。 */
    public static final String VAR_APPROVAL_USER_IDS = "approvalUserIds";
    /** DMN 计算出的本次流程所需审批人数。 */
    public static final String VAR_REQUIRED_APPROVAL_COUNT = "requiredApprovalCount";
    /** 汇总节点计算出的最终决定，取 APPROVE 或 REJECT。 */
    public static final String VAR_FINAL_DECISION = "finalDecision";
    /** 汇总节点保存的最终审批意见，通常来自驳回记录。 */
    public static final String VAR_FINAL_COMMENT = "finalComment";

    /** 单经理审批任务的 BPMN activity key。 */
    public static final String TASK_MANAGER_APPROVAL = "managerApproval";
    /** 单独 HR 审批任务的 BPMN activity key。 */
    public static final String TASK_HR_APPROVAL = "hrApproval";
    /** 经理、HR、负责人并行多实例审批任务的 BPMN activity key。 */
    public static final String TASK_PARALLEL_APPROVAL = "parallelApproval";


    private FlowableNames() {
    }
}
