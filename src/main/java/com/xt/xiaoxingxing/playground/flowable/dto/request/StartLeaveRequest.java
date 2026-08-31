package com.xt.xiaoxingxing.playground.flowable.dto.request;

import com.xt.xiaoxingxing.playground.flowable.enums.LeaveType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 启动请假审批流程所需的业务输入；审批人由服务端按路线解析，不由调用方传入。 */
@Data
public class StartLeaveRequest {

    @NotBlank(message = "申请单号不能为空")
    @Size(max = 64, message = "申请单号不能超过64个字符")
    private String requestNo;

    @NotNull(message = "申请人ID不能为空")
    @Positive(message = "申请人ID必须大于0")
    private Long applicantId;

    @NotNull(message = "请假天数不能为空")
    @Min(value = 1, message = "请假天数不能小于1天")
    @Max(value = 30, message = "请假天数不能超过30天")
    private Integer leaveDays;

    @NotNull(message = "请假类型不能为空")
    private LeaveType leaveType;

    @NotBlank(message = "请假原因不能为空")
    @Size(max = 500, message = "请假原因不能超过500个字符")
    private String reason;
}
