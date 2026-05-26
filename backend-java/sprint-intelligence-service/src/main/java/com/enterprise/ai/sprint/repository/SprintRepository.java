package com.enterprise.ai.sprint.repository;

import com.enterprise.ai.sprint.model.Sprint;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Reactive repository for Sprint entities.
 * R2DBC will auto-implement query methods at startup.
 */
@Repository
public interface SprintRepository extends ReactiveCrudRepository<Sprint, String> {

    /**
     * Find all sprints with a given status (e.g. ACTIVE, PLANNING, COMPLETED).
     */
    @Query("SELECT * FROM sprints WHERE status = :status ORDER BY start_date DESC")
    Flux<Sprint> findByStatus(String status);
}
