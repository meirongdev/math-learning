package com.mathlearning.agent;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.service.RagRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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
 * <li>CPA Designer Agent + Persona Agent - run concurrently after the Planner
 * completes</li>
 * </ol>
 */
@Service
public class MathSolverOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(MathSolverOrchestrator.class);

	private final ChatClient chatClient;
	private final RagRetrievalService ragRetrievalService;

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

	public MathSolverOrchestrator(ChatClient chatClient, RagRetrievalService ragRetrievalService) {
		this.chatClient = chatClient;
		this.ragRetrievalService = ragRetrievalService;
	}

	/**
	 * Runs the full RAG-enhanced multi-agent pipeline.
	 * <ol>
	 * <li>RAG retrieval: search for similar PSLE questions (grade-filtered)</li>
	 * <li>Planner Agent: analyze with RAG context</li>
	 * <li>CPA Designer + Persona Agents: run concurrently</li>
	 * </ol>
	 */
	public SolveResult solve(SolveRequest request) {
		// Step 0: RAG retrieval - find similar questions from vector store
		log.info("Starting RAG retrieval for grade {} question", request.grade());
		List<Document> similarQuestions = ragRetrievalService.retrieveSimilarQuestions(request.question(),
				request.grade());
		String ragContext = ragRetrievalService.formatAsContext(similarQuestions);
		log.info("RAG retrieval completed, found {} similar questions", similarQuestions.size());

		// Step 1: Run Planner Agent with RAG context
		log.info("Starting Planner Agent for grade {} question", request.grade());
		long plannerStart = System.currentTimeMillis();
		String plannerResult = runPlannerAgent(request, ragContext);
		log.info("Planner Agent completed in {}ms", System.currentTimeMillis() - plannerStart);
		log.debug("Planner raw response: {}", plannerResult);

		// Step 2: Run Content Agent (bar model + parent guide + child script in one
		// call)
		log.info("Starting Content Agent for grade {} question", request.grade());
		long contentStart = System.currentTimeMillis();
		String contentResult = runContentAgent(plannerResult, request.grade());
		log.info("Content Agent completed in {}ms", System.currentTimeMillis() - contentStart);
		log.debug("Content raw response: {}", contentResult);

		List<String> knowledgeTags = extractKnowledgeTags(plannerResult);
		String parentGuide = extractJsonField(contentResult, "parentGuide");
		String childScript = extractJsonField(contentResult, "childScript");
		String barModelJson = extractNestedJson(contentResult, "barModel");

		log.info("All agents completed successfully");
		return new SolveResult(parentGuide, childScript, barModelJson, knowledgeTags);
	}

	private String callLlm(String systemPrompt, String userMessage) {
		return chatClient.prompt().system(systemPrompt).user(userMessage)
				.options(OllamaChatOptions.builder().disableThinking().build()).call().content();
	}

	private String runPlannerAgent(SolveRequest request, String ragContext) {
		String userMessage = """
				Grade: P%d
				Question: %s

				%s
				""".formatted(request.grade(), request.question(), ragContext);
		return callLlm(PLANNER_SYSTEM_PROMPT, userMessage);
	}

	private String runContentAgent(String solutionPlan, int grade) {
		String userMessage = "Grade: P%d\nSolution plan:\n%s".formatted(grade, solutionPlan);
		return callLlm(CONTENT_AGENT_SYSTEM_PROMPT, userMessage);
	}

	/**
	 * Extracts knowledge tags from the planner JSON response. This is a simple
	 * extraction; a production system would use proper JSON parsing.
	 */
	private List<String> extractKnowledgeTags(String plannerResult) {
		try {
			int start = plannerResult.indexOf("\"knowledgeTags\"");
			if (start == -1)
				return List.of();
			int arrayStart = plannerResult.indexOf('[', start);
			int arrayEnd = plannerResult.indexOf(']', arrayStart);
			if (arrayStart == -1 || arrayEnd == -1)
				return List.of();
			String arrayContent = plannerResult.substring(arrayStart + 1, arrayEnd);
			return Arrays.stream(arrayContent.split(",")).map(s -> s.trim().replace("\"", "")).filter(s -> !s.isBlank())
					.toList();
		} catch (Exception e) {
			log.warn("Failed to extract knowledge tags from planner result", e);
			return List.of();
		}
	}

	/**
	 * Extracts a nested JSON object value (e.g. "barModel": {...}) as a raw JSON
	 * string.
	 */
	private String extractNestedJson(String json, String fieldName) {
		try {
			String key = "\"" + fieldName + "\"";
			int keyIndex = json.indexOf(key);
			if (keyIndex == -1)
				return "{}";
			int colonIndex = json.indexOf(':', keyIndex);
			int objStart = json.indexOf('{', colonIndex + 1);
			if (objStart == -1)
				return "{}";
			int depth = 0;
			int objEnd = objStart;
			while (objEnd < json.length()) {
				char c = json.charAt(objEnd);
				if (c == '{')
					depth++;
				else if (c == '}') {
					depth--;
					if (depth == 0)
						break;
				}
				objEnd++;
			}
			return json.substring(objStart, objEnd + 1);
		} catch (Exception e) {
			log.warn("Failed to extract nested JSON field '{}'", fieldName, e);
			return "{}";
		}
	}

	/**
	 * Extracts a specific field value from a JSON string. This is a simple
	 * extraction; a production system would use proper JSON parsing.
	 */
	private String extractJsonField(String json, String fieldName) {
		try {
			String key = "\"" + fieldName + "\"";
			int keyIndex = json.indexOf(key);
			if (keyIndex == -1)
				return "";
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
