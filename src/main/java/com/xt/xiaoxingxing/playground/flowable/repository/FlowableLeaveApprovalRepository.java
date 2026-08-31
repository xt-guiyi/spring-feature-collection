package com.xt.xiaoxingxing.playground.flowable.repository;

import com.xt.xiaoxingxing.playground.flowable.entity.FlowableLeaveApproval;
import com.xt.xiaoxingxing.playground.flowable.mapper.FlowableLeaveApprovalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 人工审批审计仓储。 */
@Repository
@RequiredArgsConstructor
public class FlowableLeaveApprovalRepository {

    private final FlowableLeaveApprovalMapper mapper;

    /** task_id 唯一约束让重复回调成为幂等无操作。 */
    public boolean insertIfAbsent(FlowableLeaveApproval approval) {
        return mapper.insertIfAbsent(approval) == 1;
    }

    public FlowableLeaveApproval findByTaskId(String taskId) {
        return mapper.selectByTaskId(taskId);
    }

    public List<FlowableLeaveApproval> findByLeaveRequestId(Long leaveRequestId) {
        return mapper.selectByLeaveRequestId(leaveRequestId);
    }
}
