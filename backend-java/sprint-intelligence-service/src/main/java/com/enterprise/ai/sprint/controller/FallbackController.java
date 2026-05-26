package com.enterprise.ai.sprint.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/sprints")
    public Mono<ResponseEntity<Map<String, Object>>> sprintFallback() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "DEGRADED",
                "message", "Sprint Intelligence Service is temporarily operating in read-only local database mode.",
                "data", Map.of(
                        "confidenceScore", 50.0,
                        "velocityTrend", 0,
                        "blockerCount", 0,
                        "status", "MAINTENANCE"
                )
        )));
    }

    @GetMapping("/ai")
    public Mono<ResponseEntity<Map<String, Object>>> aiFallback() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "DEGRADED",
                "message", "AI Cognitive Engine is unreachable. Relying on cached rule-based heuristics.",
                "actionItems", List.of(
                        "Please manually review task allocations on the Azure DevOps board.",
                        "Direct live speech stands are offline. Log text summaries manually."
                )
        )));
    }
}
