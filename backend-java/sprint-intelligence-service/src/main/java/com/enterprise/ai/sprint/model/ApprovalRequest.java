package com.enterprise.ai.sprint.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapping to the {@code approval_requests} table.
 * Represents a human-in-the-loop approval gate for an AI-suggested action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("approval_requests")
public class ApprovalRequest {

    @Id
    private UUID id;

    @Column("action_type")
    private ActionType actionType;

    @Column("target_work_item_id")
    private String targetWorkItemId;

    @Column("ai_reasoning")
    private String aiReasoning;

    @Column("risk_level")
    private RiskLevel riskLevel;

    private Status status;

    @Column("sprint_id")
    private String sprintId;

    @Column("requested_at")
    private OffsetDateTime requestedAt;

    @Column("decided_by")
    private String decidedBy;

    @Column("decided_at")
    private OffsetDateTime decidedAt;

    // ── Enums ──────────────────────────────────────────

    public enum ActionType {
        REASSIGN_TICKET,
        CHANGE_PRIORITY,
        FLAG_BLOCKER,
        ADJUST_SCOPE
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}
