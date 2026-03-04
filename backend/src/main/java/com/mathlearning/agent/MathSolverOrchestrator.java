package com.mathlearning.agent;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * Orchestrates the multi-agent math solving pipeline using Java 25 Structured Concurrency.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Planner Agent - analyzes the question, extracts knowledge points, creates a step-by-step solution plan</li>
 *   <li>CPA Designer Agent + Persona Agent - run concurrently after the Planner completes</li>
 * </ol>
 */
@Service
public class MathSolverOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MathSolverOrchestrator.class);

    private final ChatClient chatClient;

    private static final String PLANNER_SYSTEM_PROMPT = """
            You are an expert Singapore primary school math teacher, specializing in the 2026 MOE syllabus.

            Given a math question and the student's grade level (P1-P6), you must:
            1. Identify the topic and relevant knowledge points (e.g., "algebra.substitution", "ratio.proportion").
            2. Break down the solution into clear, numbered steps suitable for the grade level.
            3. Use the CPA (Concrete-Pictorial-Abstract) teaching approach in your explanation.

            Respond in the following JSON format:
            {
              "knowledgeTags": ["tag1", "tag2"],
              "steps": [
                {"stepNumber": 1, "description": "...", "calculation": "..."}
              ],
              "answer": "...",
              "difficulty": "easy|medium|hard"
            }
            """;

    private static final String CPA_DESIGNER_SYSTEM_PROMPT = """
            You are a CPA (Concrete-Pictorial-Abstract) visualization designer for Singapore primary math.

            Given a structured solution plan (JSON), convert it into a Bar Model description that can be
            rendered visually on a web frontend. The Bar Model is a core technique in Singapore Math education.

            Respond in the following JSON format:
            {
              "title": "Bar Model for this problem",
              "bars": [
                {
                  "label": "Total apples",
                  "segments": [
                    {"value": 5, "color": "#4CAF50", "label": "x (known)"},
                    {"value": 3, "color": "#FF9800", "label": "+3 from mom"}
                  ]
                }
              ],
              "annotations": ["x = 5, so total = 5 + 3 = 8"]
            }
            """;

    private static final String PERSONA_PARENT_SYSTEM_PROMPT = """
            You are an educational content writer for Singapore primary school math.
            Given a structured solution plan, generate TWO outputs:

            1. **Parent Guide**: A concise teaching guide for the parent, explaining the CPA method,
               what knowledge points are tested, and how to guide their child through the problem.
               Write in a supportive, professional tone.

            2. **Child Script**: A fun, engaging explanation for the child (age 7-12).
               Use analogies, emojis, and simple language. Make math feel like an adventure.
               Adapt complexity to the student's grade level.

            Respond in the following JSON format:
            {
              "parentGuide": "...",
              "childScript": "..."
            }
            """;

    public MathSolverOrchestrator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Runs the full 3-agent pipeline synchronously.
     * The Planner runs first, then the CPA Designer and Persona agents run concurrently
     * using Java 25 StructuredTaskScope.
     */
    public SolveResult solve(SolveRequest request) {
        // Step 1: Run Planner Agent
        log.info("Starting Planner Agent for grade {} question", request.grade());
        String plannerResult = runPlannerAgent(request);
        log.info("Planner Agent completed");

        // Step 2: Run CPA Designer and Persona agents concurrently
        try (var scope = StructuredTaskScope.open()) {

            var cpaFuture = scope.fork(() -> runCpaDesignerAgent(plannerResult));
            var personaFuture = scope.fork(() -> runPersonaAgent(plannerResult, request.grade()));

            scope.join();

            String barModelJson = cpaFuture.get();
            String personaResult = personaFuture.get();

            // Parse knowledge tags from planner result
            List<String> knowledgeTags = extractKnowledgeTags(plannerResult);

            // Parse parent guide and child script from persona result
            String parentGuide = extractJsonField(personaResult, "parentGuide");
            String childScript = extractJsonField(personaResult, "childScript");

            log.info("All agents completed successfully");
            return new SolveResult(parentGuide, childScript, barModelJson, knowledgeTags);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent pipeline was interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Agent pipeline failed", e);
        }
    }

    private String runPlannerAgent(SolveRequest request) {
        String userMessage = "Grade: P%d\nQuestion: %s".formatted(request.grade(), request.question());
        return chatClient.prompt()
                .system(PLANNER_SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }

    private String runCpaDesignerAgent(String solutionPlan) {
        log.info("Starting CPA Designer Agent");
        String result = chatClient.prompt()
                .system(CPA_DESIGNER_SYSTEM_PROMPT)
                .user("Solution plan:\n" + solutionPlan)
                .call()
                .content();
        log.info("CPA Designer Agent completed");
        return result;
    }

    private String runPersonaAgent(String solutionPlan, int grade) {
        log.info("Starting Persona Agent");
        String userMessage = "Grade level: P%d\nSolution plan:\n%s".formatted(grade, solutionPlan);
        String result = chatClient.prompt()
                .system(PERSONA_PARENT_SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
        log.info("Persona Agent completed");
        return result;
    }

    /**
     * Extracts knowledge tags from the planner JSON response.
     * This is a simple extraction; a production system would use proper JSON parsing.
     */
    private List<String> extractKnowledgeTags(String plannerResult) {
        try {
            int start = plannerResult.indexOf("\"knowledgeTags\"");
            if (start == -1) return List.of();
            int arrayStart = plannerResult.indexOf('[', start);
            int arrayEnd = plannerResult.indexOf(']', arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return List.of();
            String arrayContent = plannerResult.substring(arrayStart + 1, arrayEnd);
            return Arrays.stream(arrayContent.split(","))
                    .map(s -> s.trim().replace("\"", ""))
                    .filter(s -> !s.isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to extract knowledge tags from planner result", e);
            return List.of();
        }
    }

    /**
     * Extracts a specific field value from a JSON string.
     * This is a simple extraction; a production system would use proper JSON parsing.
     */
    private String extractJsonField(String json, String fieldName) {
        try {
            String key = "\"" + fieldName + "\"";
            int keyIndex = json.indexOf(key);
            if (keyIndex == -1) return "";
            int colonIndex = json.indexOf(':', keyIndex);
            int valueStart = json.indexOf('"', colonIndex + 1);
            // Find the closing quote, handling escaped quotes
            int valueEnd = valueStart + 1;
            while (valueEnd < json.length()) {
                if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd - 1) != '\\') {
                    break;
                }
                valueEnd++;
            }
            return json.substring(valueStart + 1, valueEnd);
        } catch (Exception e) {
            log.warn("Failed to extract field '{}' from JSON", fieldName, e);
            return "";
        }
    }
}
