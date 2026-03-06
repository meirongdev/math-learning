package com.mathlearning.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathlearning.model.ExplanationMode;
import com.mathlearning.model.SolveRequest;
import com.mathlearning.service.RagRetrievalService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MathSolverOrchestratorTest {

	@Mock
	ChatClient chatClient;

	@Mock
	RagRetrievalService ragRetrievalService;

	MathSolverOrchestrator orchestrator;

	// ── Valid JSON fixtures ──────────────────────────────────────────────────

	static final String PLANNER_JSON = """
			{"knowledgeTags":["whole_numbers","basic_fractions"],
			 "steps":[{"stepNumber":1,"description":"Add numbers","calculation":"5+3=8"}],
			 "answer":"8","difficulty":"easy"}
			""";

	static final String CONTENT_JSON = """
			{"barModel":{"title":"Addition","bars":[{"label":"Total","segments":[{"value":8,"color":"#4CAF50","label":"Sum"}]}]},
			 "parentGuide":"This question tests addition of whole numbers.",
			 "childScript":"Let's count together! 5 plus 3 equals 8!"}
			""";

	@BeforeEach
	void setUp() {
		// Minimal retry (1 attempt = no retry) and default circuit breaker for unit
		// tests
		Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ofMillis(10)).build());
		CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
		orchestrator = new MathSolverOrchestrator(chatClient, ragRetrievalService, new ObjectMapper(), retry, cb, 60);
	}

	/**
	 * Stubs the full ChatClient fluent chain:
	 * prompt→system→user→options→call→content
	 */
	private void stubLlm(String firstResponse, String secondResponse) {
		var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		var callSpec = mock(ChatClient.CallResponseSpec.class);

		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.options(any())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callSpec);

		// First call returns planner response, second returns content response
		when(callSpec.content()).thenReturn(firstResponse, secondResponse);
	}

	private void stubRag(List<Document> docs) {
		when(ragRetrievalService.retrieveSimilarQuestions(anyString(), any(int.class))).thenReturn(docs);
		when(ragRetrievalService.formatAsContext(any())).thenCallRealMethod();
	}

	// ── Happy path ───────────────────────────────────────────────────────────

	@Test
	void solve_validResponses_returnsPopulatedSolveResult() {
		stubLlm(PLANNER_JSON, CONTENT_JSON);
		stubRag(List.of());

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		assertNotNull(result);
		assertEquals("This question tests addition of whole numbers.", result.parentGuide());
		assertEquals("Let's count together! 5 plus 3 equals 8!", result.childScript());
		assertFalse(result.barModelJson().isBlank(), "barModelJson should be set");
		assertEquals(List.of("whole_numbers", "basic_fractions"), result.knowledgeTags());
	}

	@Test
	void solve_withRagContext_includesContextInResult() {
		stubLlm(PLANNER_JSON, CONTENT_JSON);
		var doc = new Document("Similar PSLE question", Map.of("topic", "addition", "difficulty", "easy"));
		when(ragRetrievalService.retrieveSimilarQuestions(anyString(), any(int.class))).thenReturn(List.of(doc));
		when(ragRetrievalService.formatAsContext(any())).thenCallRealMethod();

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		// Result should still be populated regardless of RAG context size
		assertNotNull(result.parentGuide());
		assertFalse(result.knowledgeTags().isEmpty());
	}

	@Test
	void solve_emptyKnowledgeTags_returnsEmptyList() {
		String plannerWithEmptyTags = """
				{"knowledgeTags":[],"steps":[],"answer":"8","difficulty":"easy"}
				""";
		stubLlm(plannerWithEmptyTags, CONTENT_JSON);
		stubRag(List.of());

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		assertNotNull(result.knowledgeTags());
		assertTrue(result.knowledgeTags().isEmpty());
	}

	@Test
	void solve_missingBarModel_returnsEmptyJsonObject() {
		String contentWithoutBarModel = """
				{"parentGuide":"Parent info","childScript":"Child info"}
				""";
		stubLlm(PLANNER_JSON, contentWithoutBarModel);
		stubRag(List.of());

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		assertEquals("{}", result.barModelJson());
	}

	// ── Parse error → fallback handling ──────────────────────────────────────

	@Test
	void solve_plannerReturnsNoJson_returnsFallbackResult() {
		stubLlm("Sorry, I cannot solve this.", CONTENT_JSON);
		stubRag(List.of());

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		assertNotNull(result);
		assertTrue(result.parentGuide().contains("couldn't be fully structured"));
		assertTrue(result.knowledgeTags().isEmpty());
	}

	@Test
	void solve_contentReturnsInvalidJson_returnsFallbackResult() {
		stubLlm(PLANNER_JSON, "not JSON at all");
		stubRag(List.of());

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		assertNotNull(result);
		assertTrue(result.parentGuide().contains("couldn't be fully structured"));
		assertEquals("not JSON at all", result.childScript());
	}

	@Test
	void solve_plannerJsonWithExtraText_stillParses() {
		// LLMs sometimes wrap JSON in markdown code blocks
		String wrappedPlanner = "Here is the plan:\n```json\n" + PLANNER_JSON.trim() + "\n```";
		stubLlm(wrappedPlanner, CONTENT_JSON);
		stubRag(List.of());

		var result = orchestrator.solve(new SolveRequest("5 + 3 = ?", 1));

		assertFalse(result.knowledgeTags().isEmpty(), "should parse JSON even when wrapped in markdown");
	}

	@Test
	void solve_socraticMode_returnsPopulatedSolveResult() {
		stubLlm(PLANNER_JSON, CONTENT_JSON);
		stubRag(List.of());

		var request = new SolveRequest("5 + 3 = ?", 1, null, ExplanationMode.SOCRATIC);
		var result = orchestrator.solve(request);

		assertNotNull(result);
		assertNotNull(result.parentGuide());
		assertNotNull(result.childScript());
		assertFalse(result.barModelJson().isBlank());
	}

	@Test
	void solve_simpleAddition_usesDeterministicFastPathWithoutLlm() {
		var request = new SolveRequest("What is 3 + 4?", 1, null, ExplanationMode.SOCRATIC);

		var result = orchestrator.solve(request);

		assertNotNull(result);
		assertTrue(result.parentGuide().contains("fast addition fact"));
		assertTrue(result.childScript().contains("What numbers do you see"));
		assertEquals(List.of("whole_numbers", "basic_arithmetic"), result.knowledgeTags());
		verifyNoInteractions(chatClient, ragRetrievalService);
	}

	@Test
	void solve_socraticMode_whenSecondPassFails_returnsPlannerDerivedFallback() {
		var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		var callSpec = mock(ChatClient.CallResponseSpec.class);

		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.options(any())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callSpec);
		when(callSpec.content()).thenReturn(PLANNER_JSON).thenThrow(new RuntimeException("timeout"));
		stubRag(List.of());

		var request = new SolveRequest("5 + 3 = ?", 1, null, ExplanationMode.SOCRATIC);
		var result = orchestrator.solve(request);

		assertNotNull(result);
		assertTrue(result.parentGuide().contains("Guide the child"));
		assertTrue(result.childScript().contains("What is the question asking us to find?"));
		assertEquals("{}", result.barModelJson());
		assertEquals(List.of("whole_numbers", "basic_fractions"), result.knowledgeTags());
	}
}
