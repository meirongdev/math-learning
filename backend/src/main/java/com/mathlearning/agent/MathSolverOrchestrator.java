package com.mathlearning.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathlearning.exception.LlmResponseParseException;
import com.mathlearning.exception.LlmTimeoutException;
import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.service.RagRetrievalService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Orchestrates the multi-agent math solving pipeline.
 *
 * <p>
 * Pipeline:
 * <ol>
 * <li>RAG Retrieval - search vector store for similar PSLE questions
 * (grade-filtered)</li>
 * <li>Planner Agent - analyzes the question with RAG context, extracts
 * knowledge points</li>
 * <li>Content Agent - generates bar model, parent guide, and child script</li>
 * </ol>
 */
@Service
public class MathSolverOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(MathSolverOrchestrator.class);

	private final int llmTimeoutSeconds;
	private final ChatClient chatClient;
	private final RagRetrievalService ragRetrievalService;
	private final ObjectMapper objectMapper;
	private final Retry retry;
	private final CircuitBreaker circuitBreaker;

	private static final String PLANNER_SYSTEM_PROMPT = """
			You are a Singapore primary math teacher (PSLE 2026 syllabus, CPA approach).
			Analyze the question and produce a step-by-step solution plan.

			Knowledge tags by grade:
			P1-P3: whole_numbers, basic_fractions, measurement, geometry
			P4: fractions.of_remainder, fractions.multiplication, decimals.money, measurement.area_perimeter
			P5: ratio.basic, ratio.before_after, average.find_missing, fractions.working_backwards
			P6: algebra.substitution, algebra.forming_equation, algebra.multi_step_equation

			Respond ONLY in JSON (no extra text):
			{"knowledgeTags":["tag"],"steps":[{"stepNumber":1,"description":"...","calculation":"..."}],"answer":"...","difficulty":"easy|medium|hard"}
			""";

	private static final String CONTENT_AGENT_SYSTEM_PROMPT = """
			You are a Singapore primary math educator (PSLE 2026). Given a solution plan (JSON), produce:

			1. barModel: Bar Model JSON with bars/segments for the key quantities.
			   Format: {"title":"...","bars":[{"label":"...","segments":[{"value":5,"color":"#4CAF50","label":"..."}]}],"annotations":["..."]}

			2. parentGuide: 2-3 sentences for the parent — topic tested, common mistake to watch, one follow-up question.

			3. childScript: Fun step-by-step explanation for the child using simple language and emojis.
			   P1-P3: use sweets/toys. P4-P5: use pizza/cards. P6: slightly mature context. End with encouragement.

			Respond ONLY in JSON (no extra text):
			{"barModel":{...},"parentGuide":"...","childScript":"..."}
			""";

	public MathSolverOrchestrator(ChatClient chatClient, RagRetrievalService ragRetrievalService,
			ObjectMapper objectMapper, Retry llmRetry, CircuitBreaker llmCircuitBreaker,
			@Value("${app.llm.timeout-seconds:180}") int llmTimeoutSeconds) {
		this.chatClient = chatClient;
		this.ragRetrievalService = ragRetrievalService;
		this.objectMapper = objectMapper;
		this.retry = llmRetry;
		this.circuitBreaker = llmCircuitBreaker;
		this.llmTimeoutSeconds = llmTimeoutSeconds;
	}

	/**
	 * Runs the full RAG-enhanced multi-agent pipeline.
	 */
	public SolveResult solve(SolveRequest request) {
		// Step 0: RAG retrieval
		log.info("Starting RAG retrieval for grade {} question", request.grade());
		List<Document> similarQuestions = ragRetrievalService.retrieveSimilarQuestions(request.question(),
				request.grade());
		String ragContext = ragRetrievalService.formatAsContext(similarQuestions);
		log.info("RAG retrieval completed, found {} similar questions", similarQuestions.size());

		// Step 1: Planner Agent
		log.info("Starting Planner Agent for grade {} question", request.grade());
		long plannerStart = System.currentTimeMillis();
		String plannerResult = callLlm(PLANNER_SYSTEM_PROMPT, buildPlannerMessage(request, ragContext));
		log.info("Planner Agent completed in {}ms", System.currentTimeMillis() - plannerStart);
		log.debug("Planner raw response: {}", plannerResult);

		// Step 2: Content Agent
		log.info("Starting Content Agent for grade {} question", request.grade());
		long contentStart = System.currentTimeMillis();
		String contentResult = callLlm(CONTENT_AGENT_SYSTEM_PROMPT,
				"Grade: P%d\nSolution plan:\n%s".formatted(request.grade(), plannerResult));
		log.info("Content Agent completed in {}ms", System.currentTimeMillis() - contentStart);
		log.debug("Content raw response: {}", contentResult);

		return parseResults(plannerResult, contentResult);
	}

	private String buildPlannerMessage(SolveRequest request, String ragContext) {
		return """
				Grade: P%d
				Question: %s

				%s
				""".formatted(request.grade(), request.question(), ragContext);
	}

	/**
	 * Calls the LLM with retry (exponential back-off) and circuit-breaker
	 * protection.
	 *
	 * @throws LlmTimeoutException
	 *             if the LLM does not respond within the timeout after all retries
	 */
	private String callLlm(String systemPrompt, String userMessage) {
		Supplier<String> supplier = () -> doCallLlm(systemPrompt, userMessage);
		Supplier<String> retryable = Retry.decorateSupplier(retry, supplier);
		Supplier<String> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retryable);
		try {
			return resilient.get();
		} catch (CallNotPermittedException e) {
			throw new LlmTimeoutException(
					"LLM service is temporarily unavailable (circuit breaker open). Please try again later.");
		}
	}

	private String doCallLlm(String systemPrompt, String userMessage) {
		var future = CompletableFuture.supplyAsync(() -> chatClient.prompt().system(systemPrompt).user(userMessage)
				.options(OllamaChatOptions.builder().disableThinking().build()).call().content());
		try {
			return future.get(llmTimeoutSeconds, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw new LlmTimeoutException(
					"LLM call timed out after " + llmTimeoutSeconds + "s. Please try again later.");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("LLM call interrupted", e);
		}
	}

	/**
	 * Parses planner and content LLM responses using Jackson. Falls back to a
	 * plain-text result when JSON parsing fails so the user always receives a
	 * readable answer.
	 */
	private SolveResult parseResults(String plannerResult, String contentResult) {
		try {
			JsonNode plannerJson = parseJson(plannerResult);
			JsonNode contentJson = parseJson(contentResult);

			List<String> knowledgeTags = objectMapper.convertValue(plannerJson.path("knowledgeTags"),
					new TypeReference<List<String>>() {
					});
			if (knowledgeTags == null) {
				knowledgeTags = List.of();
			}

			String parentGuide = contentJson.path("parentGuide").asText("");
			String childScript = contentJson.path("childScript").asText("");
			String barModelJson = contentJson.has("barModel")
					? objectMapper.writeValueAsString(contentJson.get("barModel"))
					: "{}";

			log.info("All agents completed successfully");
			return new SolveResult(parentGuide, childScript, barModelJson, knowledgeTags);
		} catch (LlmResponseParseException e) {
			log.warn("Failed to parse LLM response as JSON, returning plain-text fallback", e);
			return createFallbackResult(plannerResult, contentResult);
		} catch (Exception e) {
			log.warn("Unexpected error parsing LLM response, returning plain-text fallback", e);
			return createFallbackResult(plannerResult, contentResult);
		}
	}

	/**
	 * Builds a best-effort {@link SolveResult} from raw LLM output when JSON
	 * parsing fails.
	 */
	private SolveResult createFallbackResult(String plannerText, String contentText) {
		String bestText = (contentText != null && !contentText.isBlank()) ? contentText : plannerText;
		return new SolveResult(
				"The AI provided an explanation but it couldn't be fully structured. Please review the text below.",
				bestText, "{}", List.of());
	}

	/**
	 * Extracts the JSON object from an LLM response string. Handles cases where the
	 * model wraps JSON in markdown code blocks or adds extra text.
	 *
	 * @throws LlmResponseParseException
	 *             if no valid JSON object is found
	 */
	private JsonNode parseJson(String text) {
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start == -1 || end == -1 || end < start) {
			throw new LlmResponseParseException("No JSON object found in LLM response: " + text);
		}
		try {
			return objectMapper.readTree(text.substring(start, end + 1));
		} catch (Exception e) {
			throw new LlmResponseParseException("Failed to parse LLM response JSON: " + e.getMessage(), e);
		}
	}
}
