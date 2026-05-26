package com.enterprise.ai.sprint.service;

import com.enterprise.ai.sprint.model.Sprint;
import com.enterprise.ai.sprint.model.SprintHealthDTO;
import com.enterprise.ai.sprint.model.SprintHealthDTO.BurndownPoint;
import com.enterprise.ai.sprint.model.SprintHealthDTO.RiskItem;
import com.enterprise.ai.sprint.model.WorkItem;
import com.enterprise.ai.sprint.repository.SprintRepository;
import com.enterprise.ai.sprint.repository.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Core analytics service that computes sprint health, builds burndown charts,
 * identifies risks, and orchestrates Azure DevOps sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintHealthService {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final AzureDevOpsClient azureDevOpsClient;

    /**
     * Build a full sprint health assessment from local cached data.
     */
    public Mono<SprintHealthDTO> getSprintHealth(String sprintId) {
        Mono<Sprint> sprintMono = sprintRepository.findById(sprintId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Sprint not found: " + sprintId)));

        Flux<WorkItem> itemsFlux = workItemRepository.findBySprintId(sprintId);

        return Mono.zip(sprintMono, itemsFlux.collectList())
                .map(tuple -> {
                    Sprint sprint = tuple.getT1();
                    List<WorkItem> items = tuple.getT2();

                    double confidence = calculateConfidenceScore(items, sprint);
                    List<RiskItem> risks = identifyRisks(items);
                    List<BurndownPoint> burndown = buildBurndownData(items, sprint);
                    int blockerCount = (int) items.stream()
                            .filter(i -> "Blocked".equalsIgnoreCase(i.getStatus()))
                            .count();
                    int velocityTrend = computeVelocityTrend(items);

                    return new SprintHealthDTO(
                            sprint.getSprintId(),
                            sprint.getName(),
                            confidence,
                            velocityTrend,
                            blockerCount,
                            sprint.getStatus(),
                            risks,
                            burndown
                    );
                })
                .doOnSuccess(h -> log.info("Computed health for sprint {}: confidence={}",
                        sprintId, h.confidenceScore()))
                .doOnError(e -> log.error("Error computing sprint health for {}: {}", sprintId, e.getMessage()));
    }

    /**
     * Return burndown data for a sprint (subset of full health payload).
     */
    public Mono<List<BurndownPoint>> getBurndownData(String sprintId) {
        return getSprintHealth(sprintId)
                .map(SprintHealthDTO::burndownData);
    }

    /**
     * Calculate a 0–100 confidence score based on:
     *  - Completion percentage (40% weight)
     *  - Blocker ratio inverted (30% weight)
     *  - Scope stability approximation (30% weight)
     */
    double calculateConfidenceScore(List<WorkItem> items, Sprint sprint) {
        if (items.isEmpty()) return 0.0;

        long total = items.size();
        long completed = items.stream()
                .filter(i -> "Closed".equalsIgnoreCase(i.getStatus())
                        || "Resolved".equalsIgnoreCase(i.getStatus())
                        || "Done".equalsIgnoreCase(i.getStatus()))
                .count();
        long blocked = items.stream()
                .filter(i -> "Blocked".equalsIgnoreCase(i.getStatus()))
                .count();

        double completionRatio = (double) completed / total;
        double blockerRatio = (double) blocked / total;

        // Time elapsed ratio (how far along the sprint timeline we are)
        double timeRatio = computeTimeElapsedRatio(sprint);

        // If we're ahead of schedule, boost confidence; behind = penalty
        double paceBonus = (timeRatio > 0)
                ? Math.min(1.0, completionRatio / timeRatio)
                : 1.0;

        double score = (completionRatio * 40.0)
                + ((1.0 - blockerRatio) * 30.0)
                + (paceBonus * 30.0);

        return Math.round(score * 100.0) / 100.0;
    }

    /**
     * Identify risks from work items: blockers, unassigned, stale items.
     */
    List<RiskItem> identifyRisks(List<WorkItem> items) {
        List<RiskItem> risks = new ArrayList<>();

        // Blocked items are HIGH risks
        items.stream()
                .filter(i -> "Blocked".equalsIgnoreCase(i.getStatus()))
                .forEach(item -> risks.add(new RiskItem(
                        "RISK-" + item.getId(),
                        "Blocked: " + item.getTitle(),
                        "HIGH",
                        "Investigate blocker and escalate. Consider reassigning or breaking down the work item."
                )));

        // Unassigned items are MEDIUM risks
        items.stream()
                .filter(i -> i.getAssignedTo() == null || i.getAssignedTo().isBlank())
                .filter(i -> !"Closed".equalsIgnoreCase(i.getStatus()))
                .forEach(item -> risks.add(new RiskItem(
                        "RISK-" + item.getId(),
                        "Unassigned: " + item.getTitle(),
                        "MEDIUM",
                        "Assign this work item to a team member to prevent sprint goal drift."
                )));

        // Items with high remaining work relative to story points
        items.stream()
                .filter(i -> i.getRemainingWork() != null && i.getStoryPoints() != null)
                .filter(i -> i.getStoryPoints() > 0 && i.getRemainingWork() > i.getStoryPoints() * 2)
                .forEach(item -> risks.add(new RiskItem(
                        "RISK-" + item.getId(),
                        "Overrun: " + item.getTitle(),
                        "CRITICAL",
                        "Remaining work significantly exceeds estimate. Consider scope reduction or additional resources."
                )));

        return risks;
    }

    /**
     * Sync work items from Azure DevOps into the local database cache.
     */
    public Mono<Void> syncFromAzureDevOps(String sprintId) {
        log.info("Starting Azure DevOps sync for sprint: {}", sprintId);

        return azureDevOpsClient.getWorkItems(null, sprintId)
                .flatMap(workItem -> workItemRepository.save(workItem)
                        .doOnSuccess(saved -> log.debug("Upserted work item: {}", saved.getId())))
                .doOnComplete(() -> log.info("Sync completed for sprint: {}", sprintId))
                .doOnError(e -> log.error("Sync failed for sprint {}: {}", sprintId, e.getMessage()))
                .then();
    }

    // ── Private helpers ──────────────────────────────────

    private List<BurndownPoint> buildBurndownData(List<WorkItem> items, Sprint sprint) {
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return List.of();
        }

        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate());
        if (totalDays <= 0) return List.of();

        double totalEffort = items.stream()
                .filter(i -> i.getStoryPoints() != null)
                .mapToDouble(WorkItem::getStoryPoints)
                .sum();

        double dailyPlannedBurn = totalEffort / totalDays;

        long elapsedDays = Math.min(totalDays,
                ChronoUnit.DAYS.between(sprint.getStartDate(), OffsetDateTime.now()));

        double totalRemaining = items.stream()
                .filter(i -> i.getRemainingWork() != null)
                .mapToDouble(WorkItem::getRemainingWork)
                .sum();

        double dailyActualBurn = (elapsedDays > 0)
                ? (totalEffort - totalRemaining) / elapsedDays
                : 0;

        return IntStream.rangeClosed(1, (int) totalDays)
                .mapToObj(day -> {
                    double planned = Math.max(0, totalEffort - (dailyPlannedBurn * day));
                    double actual = (day <= elapsedDays)
                            ? Math.max(0, totalEffort - (dailyActualBurn * day))
                            : -1; // -1 indicates future (no data yet)
                    return new BurndownPoint(day, Math.round(planned * 10.0) / 10.0,
                            actual >= 0 ? Math.round(actual * 10.0) / 10.0 : -1);
                })
                .collect(Collectors.toList());
    }

    private int computeVelocityTrend(List<WorkItem> items) {
        return (int) items.stream()
                .filter(i -> "Closed".equalsIgnoreCase(i.getStatus())
                        || "Resolved".equalsIgnoreCase(i.getStatus())
                        || "Done".equalsIgnoreCase(i.getStatus()))
                .filter(i -> i.getStoryPoints() != null)
                .mapToInt(WorkItem::getStoryPoints)
                .sum();
    }

    private double computeTimeElapsedRatio(Sprint sprint) {
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return 0.5; // default assumption: midway
        }
        long totalMs = ChronoUnit.MILLIS.between(sprint.getStartDate(), sprint.getEndDate());
        long elapsedMs = ChronoUnit.MILLIS.between(sprint.getStartDate(), OffsetDateTime.now());
        if (totalMs <= 0) return 1.0;
        return Math.min(1.0, Math.max(0.0, (double) elapsedMs / totalMs));
    }
}
