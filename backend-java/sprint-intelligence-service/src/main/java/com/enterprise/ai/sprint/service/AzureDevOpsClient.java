package com.enterprise.ai.sprint.service;

import com.enterprise.ai.sprint.model.Sprint;
import com.enterprise.ai.sprint.model.WorkItem;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Reactive client for the Azure DevOps REST API (v7.1).
 * Handles work-item queries, state transitions, and sprint iteration discovery.
 */
@Slf4j
@Service
public class AzureDevOpsClient {

    private final WebClient webClient;
    private final String organization;
    private final String project;
    private final String apiVersion;

    public AzureDevOpsClient(
            @Value("${azure.devops.organization}") String organization,
            @Value("${azure.devops.project}") String project,
            @Value("${azure.devops.pat}") String pat,
            @Value("${azure.devops.api-version:7.1}") String apiVersion
    ) {
        this.organization = organization;
        this.project = project;
        this.apiVersion = apiVersion;

        String credentials = Base64.getEncoder()
                .encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl("https://dev.azure.com/" + organization + "/" + project + "/_apis")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetch all work items for a given iteration path using a WIQL query.
     */
    public Flux<WorkItem> getWorkItems(String projectId, String iterationPath) {
        String wiql = String.format(
                "SELECT [System.Id] FROM WorkItems WHERE [System.IterationPath] = '%s'",
                iterationPath
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/wit/wiql")
                        .queryParam("api-version", apiVersion)
                        .build())
                .bodyValue(Map.of("query", wiql))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(response -> {
                    JsonNode workItems = response.get("workItems");
                    if (workItems == null || !workItems.isArray() || workItems.isEmpty()) {
                        return Flux.empty();
                    }

                    // Collect IDs for batch fetch
                    List<String> ids = new java.util.ArrayList<>();
                    workItems.forEach(node -> ids.add(node.get("id").asText()));

                    return fetchWorkItemDetails(ids, iterationPath);
                })
                .doOnError(e -> log.error("Failed to fetch work items for iteration {}: {}",
                        iterationPath, e.getMessage()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Azure DevOps API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
                    return Flux.empty();
                });
    }

    /**
     * Batch-fetch work item details by IDs (max 200 per call per Azure API limits).
     */
    private Flux<WorkItem> fetchWorkItemDetails(List<String> ids, String sprintId) {
        String idsCsv = String.join(",", ids);
        String fields = "System.Id,System.Title,System.AssignedTo,System.State,Microsoft.VSTS.Scheduling.Effort,Microsoft.VSTS.Scheduling.RemainingWork";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/wit/workitems")
                        .queryParam("ids", idsCsv)
                        .queryParam("fields", fields)
                        .queryParam("api-version", apiVersion)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(response -> {
                    JsonNode items = response.get("value");
                    if (items == null || !items.isArray()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(() -> items.iterator())
                            .map(node -> mapToWorkItem(node, sprintId));
                });
    }

    private WorkItem mapToWorkItem(JsonNode node, String sprintId) {
        JsonNode fields = node.get("fields");
        return WorkItem.builder()
                .id(node.get("id").asText())
                .sprintId(sprintId)
                .title(getTextSafe(fields, "System.Title"))
                .assignedTo(extractDisplayName(fields, "System.AssignedTo"))
                .status(getTextSafe(fields, "System.State"))
                .storyPoints(getIntSafe(fields, "Microsoft.VSTS.Scheduling.Effort"))
                .remainingWork(getIntSafe(fields, "Microsoft.VSTS.Scheduling.RemainingWork"))
                .build();
    }

    /**
     * Update a work item's state (e.g., "Active" → "Resolved").
     */
    public Mono<Void> updateWorkItemState(String workItemId, String newState) {
        List<Map<String, Object>> patchDocument = List.of(
                Map.of(
                        "op", "replace",
                        "path", "/fields/System.State",
                        "value", newState
                )
        );

        return webClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/wit/workitems/{id}")
                        .queryParam("api-version", apiVersion)
                        .build(workItemId))
                .header(HttpHeaders.CONTENT_TYPE, "application/json-patch+json")
                .bodyValue(patchDocument)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Updated work item {} to state: {}", workItemId, newState))
                .doOnError(e -> log.error("Failed to update work item {}: {}", workItemId, e.getMessage()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Azure DevOps patch error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.empty();
                });
    }

    /**
     * Discover all sprint iterations for the project.
     */
    public Flux<Sprint> getSprintIterations(String projectId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/work/teamsettings/iterations")
                        .queryParam("api-version", apiVersion)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(response -> {
                    JsonNode values = response.get("value");
                    if (values == null || !values.isArray()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(() -> values.iterator())
                            .map(this::mapToSprint);
                })
                .doOnError(e -> log.error("Failed to fetch iterations: {}", e.getMessage()))
                .onErrorResume(WebClientResponseException.class, e -> Flux.empty());
    }

    private Sprint mapToSprint(JsonNode node) {
        JsonNode attributes = node.get("attributes");
        return Sprint.builder()
                .sprintId(node.get("id").asText())
                .name(node.get("name").asText())
                .startDate(parseDateTime(attributes, "startDate"))
                .endDate(parseDateTime(attributes, "finishDate"))
                .status(mapTimeFrame(getTextSafe(attributes, "timeFrame")))
                .build();
    }

    /**
     * Create a new work item in Azure DevOps.
     */
    public Mono<WorkItem> createWorkItem(String projectId, String type, Map<String, String> fieldValues) {
        List<Map<String, Object>> patchDocument = fieldValues.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "op", "add",
                        "path", "/fields/" + entry.getKey(),
                        "value", entry.getValue()
                ))
                .toList();

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/wit/workitems/${type}")
                        .queryParam("api-version", apiVersion)
                        .build(type))
                .header(HttpHeaders.CONTENT_TYPE, "application/json-patch+json")
                .bodyValue(patchDocument)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> mapToWorkItem(node, ""))
                .doOnError(e -> log.error("Failed to create work item of type {}: {}", type, e.getMessage()));
    }

    // ── Helpers ──────────────────────────────────────────

    private String getTextSafe(JsonNode parent, String field) {
        if (parent == null || !parent.has(field) || parent.get(field).isNull()) {
            return null;
        }
        return parent.get(field).asText();
    }

    private Integer getIntSafe(JsonNode parent, String field) {
        if (parent == null || !parent.has(field) || parent.get(field).isNull()) {
            return null;
        }
        return parent.get(field).asInt();
    }

    private String extractDisplayName(JsonNode fields, String field) {
        if (fields == null || !fields.has(field) || fields.get(field).isNull()) {
            return null;
        }
        JsonNode assignee = fields.get(field);
        if (assignee.isObject() && assignee.has("displayName")) {
            return assignee.get("displayName").asText();
        }
        return assignee.asText();
    }

    private OffsetDateTime parseDateTime(JsonNode parent, String field) {
        String raw = getTextSafe(parent, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception e) {
            log.warn("Failed to parse date '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    private String mapTimeFrame(String timeFrame) {
        if (timeFrame == null) return "PLANNING";
        return switch (timeFrame.toLowerCase()) {
            case "current" -> "ACTIVE";
            case "past" -> "COMPLETED";
            default -> "PLANNING";
        };
    }
}
