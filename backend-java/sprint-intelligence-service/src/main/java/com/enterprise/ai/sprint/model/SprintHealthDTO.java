package com.enterprise.ai.sprint.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Aggregate DTO representing the health analytics for a single sprint.
 * Built from work-item telemetry, velocity history, and AI risk analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SprintHealthDTO(
        String sprintId,
        String sprintName,
        double confidenceScore,
        int velocityTrend,
        int blockerCount,
        String status,
        List<RiskItem> risks,
        List<BurndownPoint> burndownData
) {

    /**
     * An individual risk identified by the AI analysis engine.
     */
    public record RiskItem(
            String id,
            String title,
            String severity,
            String recommendation
    ) {}

    /**
     * A single data point on the sprint burndown chart.
     */
    public record BurndownPoint(
            int day,
            double planned,
            double actual
    ) {}
}
