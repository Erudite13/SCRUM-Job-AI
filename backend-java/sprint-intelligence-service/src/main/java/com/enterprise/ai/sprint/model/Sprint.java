package com.enterprise.ai.sprint.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * R2DBC entity mapping to the {@code sprints} table.
 * Represents a sprint/iteration synced from Azure DevOps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sprints")
public class Sprint {

    @Id
    @Column("sprint_id")
    private String sprintId;

    private String name;

    @Column("start_date")
    private OffsetDateTime startDate;

    @Column("end_date")
    private OffsetDateTime endDate;

    @Column("velocity_forecast")
    private Double velocityForecast;

    private String status;
}
