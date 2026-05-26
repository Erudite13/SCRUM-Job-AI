package com.enterprise.ai.sprint.repository;

import com.enterprise.ai.sprint.model.ApprovalRequest;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Reactive repository for ApprovalRequest entities (human-in-the-loop workflow).
 */
@Repository
public interface ApprovalRepository extends ReactiveCrudRepository<ApprovalRequest, UUID> {

    /**
     * Find all approval requests with a given status.
     */
    @Query("SELECT * FROM approval_requests WHERE status = :status ORDER BY requested_at DESC")
    Flux<ApprovalRequest> findByStatus(String status);

    /**
     * Find all approval requests for a specific sprint.
     */
    @Query("SELECT * FROM approval_requests WHERE sprint_id = :sprintId ORDER BY requested_at DESC")
    Flux<ApprovalRequest> findBySprintId(String sprintId);

    /**
     * Find approval requests for a sprint filtered by status.
     */
    @Query("SELECT * FROM approval_requests WHERE sprint_id = :sprintId AND status = :status ORDER BY requested_at DESC")
    Flux<ApprovalRequest> findBySprintIdAndStatus(String sprintId, String status);
}
