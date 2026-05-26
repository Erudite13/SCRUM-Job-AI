package com.enterprise.ai.sprint.controller;

import com.enterprise.ai.sprint.model.ApprovalRequest;
import com.enterprise.ai.sprint.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('SCOPE_vso.work_write')")
    public Flux<ApprovalRequest> getPendingApprovals() {
        return approvalService.getPendingApprovals();
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('SCOPE_vso.work_write')")
    public Flux<ApprovalRequest> getApprovalHistory(@RequestParam String sprintId) {
        return approvalService.getApprovalHistory(sprintId);
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('SCOPE_vso.work_write')")
    public Mono<ResponseEntity<ApprovalRequest>> executeApproval(
            @PathVariable UUID id,
            @RequestParam boolean approved,
            Principal principal) {
        String userId = (principal != null) ? principal.getName() : "anonymous-manager";
        return approvalService.executeApproval(id, approved, userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('SCOPE_vso.work_write')")
    public Flux<ApprovalRequest> bulkExecute(
            @RequestBody List<UUID> ids,
            @RequestParam boolean approved,
            Principal principal) {
        String userId = (principal != null) ? principal.getName() : "anonymous-manager";
        return approvalService.bulkExecute(ids, approved, userId);
    }
}
