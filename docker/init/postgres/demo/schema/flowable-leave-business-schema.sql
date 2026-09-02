-- 本项目流程引擎业务表，不属于 Flowable 官方 ACT_*/FLW_* 系统表。
-- 它们与 Flowable 系统表分开维护，不创建数据库外键。

CREATE TABLE flowable_leave_request (
    id BIGSERIAL PRIMARY KEY,
    request_no VARCHAR(64) UNIQUE NOT NULL,
    applicant_id BIGINT NOT NULL,
    manager_id BIGINT NOT NULL,
    hr_id BIGINT,
    leader_id BIGINT,
    leave_days INTEGER NOT NULL CHECK (leave_days BETWEEN 1 AND 30),
    leave_type VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    approval_route VARCHAR(40),
    process_instance_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING', 'APPROVED', 'REJECTED')),
    final_decision VARCHAR(20),
    final_comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE flowable_leave_approval (
    id BIGSERIAL PRIMARY KEY,
    leave_request_id BIGINT NOT NULL,
    task_id VARCHAR(64) UNIQUE NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_role VARCHAR(20) NOT NULL,
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVE', 'REJECT')),
    comment VARCHAR(1000),
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flowable_leave_approval_request_task UNIQUE (leave_request_id, task_id)
);

CREATE INDEX idx_flowable_leave_request_status
    ON flowable_leave_request (status);
CREATE INDEX idx_flowable_leave_request_applicant
    ON flowable_leave_request (applicant_id);
CREATE INDEX idx_flowable_leave_request_process_instance
    ON flowable_leave_request (process_instance_id);
CREATE INDEX idx_flowable_leave_approval_request
    ON flowable_leave_approval (leave_request_id);
CREATE INDEX idx_flowable_leave_approval_approver
    ON flowable_leave_approval (approver_id);
