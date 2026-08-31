package com.xt.xiaoxingxing.playground.flowable.mapper;

import com.xt.xiaoxingxing.playground.flowable.entity.FlowableLeaveApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 请假人工审批记录数据访问。 */
@Mapper
public interface FlowableLeaveApprovalMapper {

    int insertIfAbsent(FlowableLeaveApproval approval);

    FlowableLeaveApproval selectByTaskId(@Param("taskId") String taskId);

    List<FlowableLeaveApproval> selectByLeaveRequestId(@Param("leaveRequestId") Long leaveRequestId);
}
