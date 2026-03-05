package com.mathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.service.SolveService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Solve endpoints providing both structured multi-agent pipeline and SSE streaming.
 * The structured endpoint uses Redis caching (24h TTL).
 */
@RestController
@RequestMapping("/api/v1/solve")
public class SolveController {

    private final SolveService solveService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SolveController(SolveService solveService) {
        this.solveService = solveService;
    }

    /**
     * Runs the full multi-agent pipeline with RAG and returns a structured JSON result.
     * Results are cached by question+grade for 24 hours.
     */
    @PostMapping
    public SolveResult solve(@RequestBody SolveRequest request) {
        return solveService.solve(request);
    }

    /**
     * Streams the multi-agent pipeline result as Server-Sent Events.
     * Each section (parentGuide, childScript, barModel, knowledgeTags) is sent
     * as a separate SSE event with JSON payload {"type": "...", "content": "..."}.
     * Results benefit from Redis caching (24h TTL) via SolveService.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> solveStream(@RequestBody SolveRequest request) {
        return Flux.defer(() -> {
            try {
                SolveResult result = solveService.solve(request);
                List<String> events = new ArrayList<>();

                if (result.parentGuide() != null && !result.parentGuide().isBlank()) {
                    events.add(objectMapper.writeValueAsString(
                            Map.of("type", "parent_guide", "content", result.parentGuide())));
                }
                if (result.childScript() != null && !result.childScript().isBlank()) {
                    events.add(objectMapper.writeValueAsString(
                            Map.of("type", "child_script", "content", result.childScript())));
                }
                if (result.barModelJson() != null && !result.barModelJson().isBlank()) {
                    events.add(objectMapper.writeValueAsString(
                            Map.of("type", "bar_model", "content", result.barModelJson())));
                }
                if (result.knowledgeTags() != null && !result.knowledgeTags().isEmpty()) {
                    events.add(objectMapper.writeValueAsString(
                            Map.of("type", "knowledge_tags", "content",
                                    String.join(", ", result.knowledgeTags()))));
                }
                events.add("[DONE]");

                return Flux.fromIterable(events);
            } catch (Exception e) {
                try {
                    return Flux.just(
                            objectMapper.writeValueAsString(
                                    Map.of("type", "error", "content",
                                            e.getMessage() != null ? e.getMessage() : "Unknown error")),
                            "[DONE]");
                } catch (Exception ex) {
                    return Flux.just("{\"type\":\"error\",\"content\":\"Internal server error\"}", "[DONE]");
                }
            }
        });
    }
}
