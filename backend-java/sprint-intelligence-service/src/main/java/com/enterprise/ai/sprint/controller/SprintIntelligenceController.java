package com.enterprise.ai.sprint.controller;

import com.enterprise.ai.sprint.model.SprintHealthDTO;
import com.enterprise.ai.sprint.service.SprintHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sprints")
@RequiredArgsConstructor
public class SprintIntelligenceController {

    private final SprintHealthService sprintHealthService;

    @GetMapping("/{sprintId}/health")
    @PreAuthorize("hasAuthority('SCOPE_vso.work_write')")
    public Mono<ResponseEntity<SprintHealthDTO>> getSprintHealth(@PathVariable String sprintId) {
        return sprintHealthService.getSprintHealth(sprintId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{sprintId}/sync")
    @PreAuthorize("hasAuthority('SCOPE_vso.work_write')")
    public Mono<ResponseEntity<Map<String, String>>> syncSprint(@PathVariable String sprintId) {
        return sprintHealthService.syncFromAzureDevOps(sprintId)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "status", "SYNCHRONIZED",
                        "message", "Successfully synchronized sprint data from Azure DevOps."
                ))))
                .onErrorResume(err -> Mono.just(ResponseEntity.status(500).body(Map.of(
                        "status", "ERROR",
                        "message", "Sync failed: " + err.getMessage()
                ))));
    }
}
