package com.xt.xiaoxingxing.playground.features.flowable.mapper;

import com.xt.xiaoxingxing.playground.features.flowable.entity.FlowableLeaveRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 请假业务台账数据访问。Flowable 自己的 ACT_* 运行表不通过本 Mapper 操作。 */
@Mapper
public interface FlowableLeaveRequestMapper {

    int insert(FlowableLeaveRequest request);

    FlowableLeaveRequest selectById(@Param("id") Long id);

    FlowableLeaveRequest selectByRequestNo(@Param("requestNo") String requestNo);

    int updateProcessInstanceId(@Param("id") Long id, @Param("processInstanceId") String processInstanceId);

    int updateApproversAndRoute(@Param("id") Long id,
                                @Param("managerId") Long managerId,
                                @Param("hrId") Long hrId,
                                @Param("leaderId") Long leaderId,
                                @Param("approvalRoute") String approvalRoute);

    int updateFinalDecision(@Param("id") Long id,
                            @Param("status") String status,
                            @Param("finalDecision") String finalDecision,
                            @Param("finalComment") String finalComment);

    List<FlowableLeaveRequest> selectPage(@Param("requestNo") String requestNo,
                                          @Param("applicantId") Long applicantId,
                                          @Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("pageSize") int pageSize);

    long countPage(@Param("requestNo") String requestNo,
                   @Param("applicantId") Long applicantId,
                   @Param("status") String status);
}
