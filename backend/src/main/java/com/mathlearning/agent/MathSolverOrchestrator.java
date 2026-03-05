package com.mathlearning.agent;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.service.RagRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * Orchestrates the multi-agent math solving pipeline using Java 25 Structured Concurrency.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>RAG Retrieval - search vector store for similar PSLE questions (grade-filtered)</li>
 *   <li>Planner Agent - analyzes the question with RAG context, extracts knowledge points</li>
 *   <li>CPA Designer Agent + Persona Agent - run concurrently after the Planner completes</li>
 * </ol>
 */
@Service
public class MathSolverOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MathSolverOrchestrator.class);

    private final ChatClient chatClient;
    private final RagRetrievalService ragRetrievalService;

    private static final String PLANNER_SYSTEM_PROMPT = """
            You are an expert Singapore primary school math teacher, specializing in the 2026 MOE PSLE syllabus.
            You follow the CPA (Concrete-Pictorial-Abstract) teaching approach strictly.

            ## PSLE 2026 Scoring Criteria
            - Full marks require: correct answer + complete working + proper units
            - Method marks: awarded for correct approach even if final answer is wrong
            - Presentation: numbered steps, one operation per step, clear labelling

            ## Your Task
            Given a math question, the student's grade level (P1-P6), and similar reference questions
            from the PSLE question bank, you must:
            1. Identify the topic and relevant knowledge points using standard codes
               (e.g., "algebra.substitution", "ratio.proportion", "fractions.of_remainder").
            2. Break down the solution into clear, numbered steps suitable for the grade level.
            3. Use the CPA approach: start with a concrete scenario, then pictorial (bar model), then abstract (equation).
            4. Reference similar questions from the knowledge base when the approach is applicable.
            5. Ensure the solution meets PSLE marking standards.

            ## Knowledge Point Codes (2026 Syllabus)
            - P4: fractions.of_remainder, fractions.of_whole, fractions.multiplication, decimals.money,
                  measurement.area_perimeter, whole_numbers.problem_solving
            - P5: ratio.basic, ratio.difference, ratio.before_after, ratio.sharing, ratio.total_known,
                  average.adding_number, average.combined_groups, average.find_missing,
                  fractions.working_backwards
            - P6: algebra.simple_equation, algebra.expression, algebra.substitution, algebra.forming_equation,
                  algebra.simplify_expression, algebra.multi_step_equation, algebra.consecutive_numbers,
                  algebra.simultaneous_concept

            Respond ONLY in the following JSON format (no extra text):
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
            You are a CPA (Concrete-Pictorial-Abstract) visualization designer for Singapore primary math,
            following the 2026 MOE syllabus and PSLE exam standards.

            The Bar Model (also called Model Method) is THE core visual technique in Singapore Math education.
            It is used extensively in PSLE exams for ratio, fractions, and algebra problems.

            ## Bar Model Design Rules
            - Use proportional bar lengths to represent quantities
            - Use distinct colors for different quantities or groups
            - Label all bars clearly with variable names and known values
            - Include bracket annotations showing totals, differences, or relationships
            - For ratio problems: draw bars side by side with equal unit lengths
            - For fraction problems: divide a single bar into equal parts
            - For algebra: use a bar with unknown length labeled with the variable

            Given a structured solution plan (JSON), convert it into a Bar Model description.

            Respond ONLY in the following JSON format (no extra text):
            {
              "title": "Bar Model for this problem",
              "bars": [
                {
                  "label": "...",
                  "segments": [
                    {"value": 5, "color": "#4CAF50", "label": "..."}
                  ]
                }
              ],
              "annotations": ["..."]
            }
            """;

    private static final String PERSONA_PARENT_SYSTEM_PROMPT = """
            You are an educational content writer for Singapore primary school math,
            aligned with the 2026 MOE PSLE syllabus.

            Given a structured solution plan, generate TWO outputs:

            ## 1. Parent Guide
            Write a concise teaching guide for the parent. Include:
            - Which PSLE topic and knowledge points are tested
            - The CPA (Concrete-Pictorial-Abstract) teaching progression to use
            - Common mistakes students make on this type of question
            - How to guide the child WITHOUT giving away the answer directly
            - One follow-up practice question suggestion at the same difficulty level
            Use a supportive, professional tone. Reference the 2026 syllabus where relevant.

            ## 2. Child Script
            Write a fun, engaging explanation for the child (age 7-12). Rules:
            - Use analogies, emojis, and simple language appropriate for the grade level
            - Make math feel like an adventure or game
            - For P1-P3: use concrete objects (sweets, toys, stickers)
            - For P4-P5: use relatable scenarios (sharing pizza, collecting cards)
            - For P6: use slightly more mature contexts while keeping it fun
            - Walk through the solution step by step in a conversational way
            - End with encouragement

            Respond ONLY in the following JSON format (no extra text):
            {
              "parentGuide": "...",
              "childScript": "..."
            }
            """;

    public MathSolverOrchestrator(ChatClient.Builder chatClientBuilder, RagRetrievalService ragRetrievalService) {
        this.chatClient = chatClientBuilder.build();
        this.ragRetrievalService = ragRetrievalService;
    }

    /**
     * Runs the full RAG-enhanced multi-agent pipeline.
     * <ol>
     *   <li>RAG retrieval: search for similar PSLE questions (grade-filtered)</li>
     *   <li>Planner Agent: analyze with RAG context</li>
     *   <li>CPA Designer + Persona Agents: run concurrently</li>
     * </ol>
     */
    public SolveResult solve(SolveRequest request) {
        // Step 0: RAG retrieval - find similar questions from vector store
        log.info("Starting RAG retrieval for grade {} question", request.grade());
        List<Document> similarQuestions = ragRetrievalService.retrieveSimilarQuestions(
                request.question(), request.grade());
        String ragContext = ragRetrievalService.formatAsContext(similarQuestions);
        log.info("RAG retrieval completed, found {} similar questions", similarQuestions.size());

        // Step 1: Run Planner Agent with RAG context
        log.info("Starting Planner Agent for grade {} question", request.grade());
        String plannerResult = runPlannerAgent(request, ragContext);
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

    private String runPlannerAgent(SolveRequest request, String ragContext) {
        String userMessage = """
                Grade: P%d
                Question: %s

                %s
                """.formatted(request.grade(), request.question(), ragContext);
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
