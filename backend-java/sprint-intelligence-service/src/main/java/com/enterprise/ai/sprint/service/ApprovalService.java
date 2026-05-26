package com.enterprise.ai.sprint.service;

import com.enterprise.ai.sprint.model.ApprovalRequest;
import com.enterprise.ai.sprint.model.ApprovalRequest.Status;
import com.enterprise.ai.sprint.repository.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the human-in-the-loop (HITL) approval workflow.
 * AI-suggested actions are staged as pending approvals;
 * once a human approves, the action is executed against Azure DevOps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final AzureDevOpsClient azureDevOpsClient;

    /**
     * Stage a new AI-suggested action for human approval.
     */
    public Mono<ApprovalRequest> createApproval(ApprovalRequest request) {
        request.setId(UUID.randomUUID());
        request.setStatus(Status.PENDING);
        request.setRequestedAt(OffsetDateTime.now());

        return approvalRepository.save(request)
                .doOnSuccess(saved -> log.info("Created approval request {} for action {} on work item {}",
                        saved.getId(), saved.getActionType(), saved.getTargetWorkItemId()));
    }

    /**
     * Execute or reject an approval. If approved, carries out the action in Azure DevOps.
     *
     * @param id       approval request ID
     * @param approved true to approve and execute, false to reject
     * @param userId   the human who made the decision (audit trail)
     */
    public Mono<ApprovalRequest> executeApproval(UUID id, boolean approved, String userId) {
        return approvalRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Approval request not found: " + id)))
                .flatMap(request -> {
                    if (request.getStatus() != Status.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Approval " + id + " is already " + request.getStatus()));
                    }

                    request.setStatus(approved ? Status.APPROVED : Status.REJECTED);
                    request.setDecidedBy(userId);
                    request.setDecidedAt(OffsetDateTime.now());

                    if (approved) {
                        return executeAction(request)
                                .then(approvalRepository.save(request))
                                .doOnSuccess(r -> log.info("Approval {} APPROVED and executed by {}",
                                        id, userId));
                    } else {
                        return approvalRepository.save(request)
                                .doOnSuccess(r -> log.info("Approval {} REJECTED by {}", id, userId));
                    }
                });
    }

    /**
     * Bulk approve or reject a batch of approval requests.
     */
    public Flux<ApprovalRequest> bulkExecute(List<UUID> ids, boolean approved, String userId) {
        return Flux.fromIterable(ids)
                .flatMap(id -> executeApproval(id, approved, userId)
                        .onErrorResume(e -> {
                            log.warn("Bulk execution skipped for {}: {}", id, e.getMessage());
                            return Mono.empty();
                        }), 4); // concurrency limit
    }

    /**
     * Get all pending approval requests.
     */
    public Flux<ApprovalRequest> getPendingApprovals() {
        return approvalRepository.findByStatus(Status.PENDING.name());
    }

    /**
     * Get full approval history for a sprint.
     */
    public Flux<ApprovalRequest> getApprovalHistory(String sprintId) {
        return approvalRepository.findBySprintId(sprintId);
    }

    // ── Action execution ─────────────────────────────────

    private Mono<Void> executeAction(ApprovalRequest request) {
        log.info("Executing {} on work item {}", request.getActionType(), request.getTargetWorkItemId());

        return switch (request.getActionType()) {
            case REASSIGN_TICKET -> azureDevOpsClient.updateWorkItemState(
                    request.getTargetWorkItemId(), "Active");

            case CHANGE_PRIORITY -> azureDevOpsClient.updateWorkItemState(
                    request.getTargetWorkItemId(), "Active");

            case FLAG_BLOCKER -> azureDevOpsClient.updateWorkItemState(
                    request.getTargetWorkItemId(), "Blocked");

            case ADJUST_SCOPE -> azureDevOpsClient.updateWorkItemState(
                    request.getTargetWorkItemId(), "Removed");
        };
    }
}
