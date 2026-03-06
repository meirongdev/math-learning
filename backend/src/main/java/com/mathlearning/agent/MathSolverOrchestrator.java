package com.mathlearning.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathlearning.exception.LlmResponseParseException;
import com.mathlearning.exception.LlmTimeoutException;
import com.mathlearning.model.ExplanationMode;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
	private static final Pattern SIMPLE_ARITHMETIC_PATTERN = Pattern.compile(
			"(?i)^\\s*(?:what\\s+is\\s+)?(\\d{1,4})\\s*([+\\-x×*/÷])\\s*(\\d{1,4})\\s*(?:=|[?？])?\\s*$");

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
		SolveResult deterministic = trySolveSimpleArithmetic(request);
		if (deterministic != null) {
			log.info("Using deterministic fast path for grade {} question", request.grade());
			return deterministic;
		}

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

		// Step 2: Content/Socratic Agent — route based on explanation mode
		ExplanationMode mode = request.effectiveMode();
		if (mode == ExplanationMode.SOCRATIC) {
			log.info("Building Socratic response from planner output for grade {} question", request.grade());
			return buildSocraticFallbackResult(plannerResult);
		}

		log.info("Starting Content Agent (mode={}) for grade {} question", mode, request.grade());
		long contentStart = System.currentTimeMillis();
		String contentResult = callLlm(CONTENT_AGENT_SYSTEM_PROMPT,
				"Grade: P%d\nSolution plan:\n%s".formatted(request.grade(), plannerResult));
		log.info("Content Agent completed in {}ms", System.currentTimeMillis() - contentStart);
		log.debug("Content raw response: {}", contentResult);

		return parseResults(plannerResult, contentResult);
	}

	private String buildPlannerMessage(SolveRequest request, String ragContext) {
		return """
				Grade: P%d%nQuestion: %s

				%s
				""".formatted(request.grade(), request.question(), ragContext);
	}

	private SolveResult trySolveSimpleArithmetic(SolveRequest request) {
		Matcher matcher = SIMPLE_ARITHMETIC_PATTERN.matcher(request.question());
		if (!matcher.matches()) {
			return null;
		}

		int left = Integer.parseInt(matcher.group(1));
		int right = Integer.parseInt(matcher.group(3));
		char operator = matcher.group(2).charAt(0);

		return switch (operator) {
			case '+' -> buildArithmeticResult(request.effectiveMode(), left, right, operator, left + right,
					"addition", List.of("whole_numbers", "basic_arithmetic"), simpleAdditionBarModel(left, right));
			case '-' -> buildArithmeticResult(request.effectiveMode(), left, right, operator, left - right,
					"subtraction", List.of("whole_numbers", "basic_arithmetic"), simpleSubtractionBarModel(left, right));
			case 'x', 'X', '×', '*' -> buildArithmeticResult(request.effectiveMode(), left, right, operator, left * right,
					"multiplication", List.of("whole_numbers", "basic_arithmetic"), "{}");
			case '/', '÷' -> {
				if (right == 0 || left % right != 0) {
					yield null;
				}
				yield buildArithmeticResult(request.effectiveMode(), left, right, operator, left / right, "division",
						List.of("whole_numbers", "basic_arithmetic"), "{}");
			}
			default -> null;
		};
	}

	private SolveResult buildArithmeticResult(ExplanationMode mode, int left, int right, char operator, int answer,
			String operationName, List<String> knowledgeTags, String barModelJson) {
		String expression = "%d %s %d = %d".formatted(left, normalizeOperator(operator), right, answer);
		String parentGuide = "This is a fast %s fact. Let the child say what each number means first, then explain why %s is the correct number sentence."
				.formatted(operationName, expression);

		String childScript = mode == ExplanationMode.SOCRATIC
				? "1. What numbers do you see in %d %s %d? 🤔%n2. Should we combine them, compare them, share them, or make equal groups?%n3. Can you say the number sentence aloud?%n4. What answer do you get now? 🎉"
						.formatted(left, normalizeOperator(operator), right)
				: "Let's solve it together! %d %s %d gives us %d. So the answer is %d. Great job!"
						.formatted(left, normalizeOperator(operator), right, answer, answer);

		return new SolveResult(parentGuide, childScript, barModelJson, knowledgeTags);
	}

	private String normalizeOperator(char operator) {
		return switch (operator) {
			case 'x', 'X', '*', '×' -> "×";
			case '/', '÷' -> "÷";
			default -> Character.toString(operator);
		};
	}

	private String simpleAdditionBarModel(int left, int right) {
		return """
				{"title":"Addition","bars":[{"label":"Total","segments":[{"value":%d,"color":"#42A5F5","label":"First part"},{"value":%d,"color":"#66BB6A","label":"Second part"}]}],"annotations":["Put the two parts together."]}
				""".formatted(left, right);
	}

	private String simpleSubtractionBarModel(int left, int right) {
		return """
				{"title":"Subtraction","bars":[{"label":"Whole","segments":[{"value":%d,"color":"#42A5F5","label":"Whole"}]},{"label":"Take away","segments":[{"value":%d,"color":"#EF5350","label":"Removed"},{"value":%d,"color":"#66BB6A","label":"Left"}]}],"annotations":["Start from the whole, then remove part of it."]}
				""".formatted(left, right, left - right);
	}

	/**
	 * Calls the LLM with retry (exponential back-off) and circuit-breaker
	 * protection.
	 *
	 * @throws LlmTimeoutException
	 *             if the LLM does not respond within the timeout after all retries
	 */
	private String callLlm(String systemPrompt, String userMessage) {
		return callLlm(systemPrompt, userMessage, llmTimeoutSeconds);
	}

	private String callLlm(String systemPrompt, String userMessage, int timeoutSeconds) {
		Supplier<String> supplier = () -> doCallLlm(systemPrompt, userMessage);
		Supplier<String> retryable = Retry.decorateSupplier(retry, supplier);
		Supplier<String> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retryable);
		try {
			return doCallWithTimeout(resilient, timeoutSeconds);
		} catch (CallNotPermittedException e) {
			throw new LlmTimeoutException(
					"LLM service is temporarily unavailable (circuit breaker open). Please try again later.");
		}
	}

	private String doCallWithTimeout(Supplier<String> resilient, int timeoutSeconds) {
		var future = CompletableFuture.supplyAsync(resilient::get);
		try {
			return future.get(timeoutSeconds, TimeUnit.SECONDS);
		} catch (TimeoutException _) {
			future.cancel(true);
			throw new LlmTimeoutException("LLM call timed out after " + timeoutSeconds + "s. Please try again later.");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("LLM call interrupted", e);
		}
	}

	private String doCallLlm(String systemPrompt, String userMessage) {
		return chatClient.prompt().system(systemPrompt).user(userMessage)
				.options(OllamaChatOptions.builder().disableThinking().build()).call().content();
	}

	private SolveResult buildSocraticFallbackResult(String plannerResult) {
		try {
			JsonNode plannerJson = parseJson(plannerResult);
			List<String> knowledgeTags = objectMapper.convertValue(plannerJson.path("knowledgeTags"),
					new TypeReference<List<String>>() {
					});
			if (knowledgeTags == null) {
				knowledgeTags = List.of();
			}

			List<JsonNode> stepPrompts = objectMapper.convertValue(plannerJson.path("steps"), new TypeReference<List<JsonNode>>() {
			});
			String answer = plannerJson.path("answer").asText("");
			String firstTag = knowledgeTags.isEmpty() ? "the main concept" : knowledgeTags.getFirst().replace('_', ' ');

			String childScript = buildSocraticQuestions(stepPrompts, answer);
			String parentGuide = "Guide the child with one question at a time. Focus on %s, wait for them to explain their thinking, and only reveal the final answer after they have tried each step."
					.formatted(firstTag);

			return new SolveResult(parentGuide, childScript, "{}", knowledgeTags);
		} catch (Exception e) {
			log.warn("Failed to build Socratic fallback from planner output, using generic fallback", e);
			return new SolveResult(
					"Ask the child to explain what the question is asking, then guide them step by step instead of giving the answer immediately.",
					"1. What numbers or facts do you notice in the question?\n2. Which operation should we try first?\n3. Can you work through the steps aloud?\n4. What answer do you get now? 🎉",
					"{}", List.of());
		}
	}

	private String buildSocraticQuestions(List<JsonNode> stepPrompts, String answer) {
		List<String> questions = stepPrompts.stream().limit(3).map(step -> {
			String description = step.path("description").asText("What should we do first?");
			String calculation = step.path("calculation").asText("");
			if (!calculation.isBlank()) {
				return "What does this step mean: %s (%s)?".formatted(description, calculation);
			}
			return "What should we do here: %s?".formatted(description);
		}).toList();

		List<String> prompts = new java.util.ArrayList<>();
		prompts.add("1. What is the question asking us to find? 🤔");
		if (questions.isEmpty()) {
			prompts.add("2. Which operation should we try first, and why?");
			prompts.add("3. Can you talk me through your working step by step?");
		} else {
			for (int i = 0; i < questions.size(); i++) {
				prompts.add((i + 2) + ". " + questions.get(i));
			}
		}
		if (!answer.isBlank()) {
			prompts.add((prompts.size() + 1) + ". Once you finish, what answer do you get? Check whether it matches %s. 🎉"
					.formatted(answer));
		} else {
			prompts.add((prompts.size() + 1) + ". Once you finish, what answer do you get? 🎉");
		}
		return String.join("\n", prompts);
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
