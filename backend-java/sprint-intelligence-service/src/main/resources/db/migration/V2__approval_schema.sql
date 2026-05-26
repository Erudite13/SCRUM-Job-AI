-- =====================================================
-- V2: Approval Workflow Schema
-- Human-in-the-loop approval requests for AI-driven actions
-- =====================================================

-- Approval Requests Table
CREATE TABLE approval_requests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type          VARCHAR(30)  NOT NULL
        CHECK (action_type IN ('REASSIGN_TICKET', 'CHANGE_PRIORITY', 'FLAG_BLOCKER', 'ADJUST_SCOPE')),
    target_work_item_id  VARCHAR(50)  NOT NULL,
    ai_reasoning         TEXT         NOT NULL,
    risk_level           VARCHAR(10)  NOT NULL
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status               VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    sprint_id            VARCHAR(50)  REFERENCES sprints(sprint_id) ON DELETE SET NULL,
    requested_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by           VARCHAR(100),
    decided_at           TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE INDEX idx_approval_status     ON approval_requests (status);
CREATE INDEX idx_approval_sprint     ON approval_requests (sprint_id);
CREATE INDEX idx_approval_sprint_status ON approval_requests (sprint_id, status);

-- Audit Trail for compliance and governance
CREATE TABLE approval_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_id     UUID         NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    action          VARCHAR(20)  NOT NULL,
    performed_by    VARCHAR(100) NOT NULL,
    details         JSONB,
    performed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_approval ON approval_audit_log (approval_id);
CREATE INDEX idx_audit_time     ON approval_audit_log (performed_at);
