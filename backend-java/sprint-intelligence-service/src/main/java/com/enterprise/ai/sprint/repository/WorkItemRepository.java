package com.enterprise.ai.sprint.repository;

import com.enterprise.ai.sprint.model.WorkItem;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for WorkItem entities synced from Azure DevOps.
 */
@Repository
public interface WorkItemRepository extends ReactiveCrudRepository<WorkItem, String> {

    /**
     * All work items belonging to a sprint.
     */
    @Query("SELECT * FROM work_items WHERE sprint_id = :sprintId ORDER BY id")
    Flux<WorkItem> findBySprintId(String sprintId);

    /**
     * Work items filtered by sprint and status (e.g. "Blocked", "Active").
     */
    @Query("SELECT * FROM work_items WHERE sprint_id = :sprintId AND status = :status ORDER BY id")
    Flux<WorkItem> findBySprintIdAndStatus(String sprintId, String status);

    /**
     * Count work items in a sprint that match a given status — used for health score math.
     */
    @Query("SELECT COUNT(*) FROM work_items WHERE sprint_id = :sprintId AND status = :status")
    Mono<Long> countBySprintIdAndStatus(String sprintId, String status);
}
