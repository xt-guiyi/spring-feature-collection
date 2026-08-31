package com.xt.xiaoxingxing.playground.flowable.repository;

import com.xt.xiaoxingxing.playground.flowable.entity.FlowableLeaveRequest;
import com.xt.xiaoxingxing.playground.flowable.mapper.FlowableLeaveRequestMapper;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 请假业务台账仓储， */
@Repository
@RequiredArgsConstructor
public class FlowableLeaveRequestRepository {

    private final FlowableLeaveRequestMapper mapper;

    public boolean insert(FlowableLeaveRequest request) {
        return mapper.insert(request) == 1;
    }

    public FlowableLeaveRequest findById(Long id) {
        return mapper.selectById(id);
    }

    public FlowableLeaveRequest findByRequestNo(String requestNo) {
        return mapper.selectByRequestNo(requestNo);
    }

    public FlowableLeaveRequest requireById(Long id) {
        return BusinessAssert.notNull(findById(id), "请假申请不存在");
    }

    public boolean attachProcessInstance(Long id, String processInstanceId) {
        return mapper.updateProcessInstanceId(id, processInstanceId) == 1;
    }

    public boolean updateApproversAndRoute(Long id, Long managerId, Long hrId,
                                           Long leaderId, String route) {
        return mapper.updateApproversAndRoute(id, managerId, hrId, leaderId, route) == 1;
    }

    public boolean finalize(Long id, String status, String decision, String comment) {
        int affected = mapper.updateFinalDecision(id, status, decision, comment);
        // 流程可能被重复结束；已是终态时是幂等成功，而不是覆盖既有决定。
        if (affected == 1) {
            return true;
        }
        FlowableLeaveRequest current = findById(id);
        return current != null && status.equals(current.getStatus());
    }

    public List<FlowableLeaveRequest> page(
            String requestNo, Long applicantId, String status, int pageNum, int pageSize) {
        long offset = (long) (pageNum - 1) * pageSize;
        return mapper.selectPage(requestNo, applicantId, status, offset, pageSize);
    }

    public long count(String requestNo, Long applicantId, String status) {
        return mapper.countPage(requestNo, applicantId, status);
    }
}
