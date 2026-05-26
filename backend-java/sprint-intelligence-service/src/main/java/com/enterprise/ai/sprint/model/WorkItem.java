package com.enterprise.ai.sprint.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity mapping to the {@code work_items} table.
 * Represents a cached Azure DevOps work item synced into the local database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("work_items")
public class WorkItem {

    @Id
    private String id;

    @Column("sprint_id")
    private String sprintId;

    private String title;

    @Column("assigned_to")
    private String assignedTo;

    private String status;

    /**
     * Maps to the {@code effort_estimate} column in the DB.
     * Domain model uses "storyPoints" for clarity.
     */
    @Column("effort_estimate")
    private Integer storyPoints;

    @Column("remaining_work")
    private Integer remainingWork;
}
