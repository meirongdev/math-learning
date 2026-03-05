package com.mathlearning.controller;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.service.SolveService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Solve endpoints providing both structured multi-agent pipeline and SSE streaming.
 * The structured endpoint uses Redis caching (24h TTL).
 */
@RestController
@RequestMapping("/api/v1/solve")
public class SolveController {

    private final ChatClient chatClient;
    private final SolveService solveService;

    private static final String SOLVE_SYSTEM_PROMPT = """
            You are an expert Singapore primary school math tutor following the 2026 MOE PSLE syllabus.
            Use the CPA (Concrete-Pictorial-Abstract) teaching method strictly.

            ## PSLE 2026 Scoring Standards
            - Show complete working with one operation per step
            - Use proper units in the final answer
            - Reference the Bar Model (Model Method) for visual explanation

            For every question, provide:
            1. Knowledge points tested (as PSLE topic codes)
            2. Step-by-step solution using age-appropriate language
            3. A Bar Model description for visual representation
            4. A parent guide explaining how to teach the concept at home
            5. A fun child-friendly script explaining the answer

            Adapt your language complexity to match the student's grade level (P1-P6).
            Respond in a structured way with clear section headers.
            """;

    public SolveController(ChatClient.Builder chatClientBuilder, SolveService solveService) {
        this.chatClient = chatClientBuilder.build();
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
     * Streams the AI response as Server-Sent Events.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> solveStream(@RequestBody SolveRequest request) {
        String userMessage = "Grade: P%d\nQuestion: %s".formatted(request.grade(), request.question());

        return chatClient.prompt()
                .system(SOLVE_SYSTEM_PROMPT)
                .user(userMessage)
                .stream()
                .content();
    }
}
