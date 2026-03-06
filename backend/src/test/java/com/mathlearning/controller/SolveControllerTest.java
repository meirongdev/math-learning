package com.mathlearning.controller;

import com.mathlearning.AbstractIntegrationTest;
import com.mathlearning.exception.LlmResponseParseException;
import com.mathlearning.exception.LlmTimeoutException;
import com.mathlearning.model.SolveResult;
import com.mathlearning.service.SolveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SolveController.
 */
class SolveControllerTest extends AbstractIntegrationTest {

	@MockitoBean
	SolveService solveService;

	@Autowired
	MockMvc mockMvc;

	private String token;

	@BeforeEach
	void setUp() {
		token = generateTestToken();
	}

	// ── Main flow ────────────────────────────────────────────────────────────

	@Test
	void solve_ValidRequest_Returns200WithResult() throws Exception {
		var result = new SolveResult("Parent guide text", "Child script text", "{}", List.of("whole_numbers"));
		when(solveService.solve(any())).thenReturn(result);

		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":1}
						""")).andExpect(status().isOk()).andExpect(jsonPath("$.parentGuide").value("Parent guide text"))
				.andExpect(jsonPath("$.childScript").value("Child script text"))
				.andExpect(jsonPath("$.knowledgeTags[0]").value("whole_numbers"));
	}

	@Test
	void solve_NoToken_Returns401() throws Exception {
		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON).content("""
				{"question":"What is 5 + 3?","grade":1}
				""")).andExpect(status().isUnauthorized());
	}

	// ── Boundary conditions ──────────────────────────────────────────────────

	@Test
	void solve_BlankQuestion_Returns400WithCode() throws Exception {
		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"","grade":3}
						""")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void solve_QuestionTooLong_Returns400() throws Exception {
		String longQuestion = "x".repeat(501);
		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"%s","grade":3}
						""".formatted(longQuestion))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void solve_GradeTooLow_Returns400() throws Exception {
		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":0}
						""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void solve_GradeTooHigh_Returns400() throws Exception {
		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":7}
						""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void solve_NullQuestion_Returns400() throws Exception {
		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"grade":3}
						""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	// ── GlobalExceptionHandler (error response format) ───────────────────────

	@Test
	void solve_LlmTimeout_Returns504WithCode() throws Exception {
		when(solveService.solve(any())).thenThrow(new LlmTimeoutException("LLM timed out after 60s"));

		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":1}
						""")).andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("LLM_TIMEOUT"))
				.andExpect(jsonPath("$.message").value("LLM timed out after 60s"));
	}

	@Test
	void solve_LlmParseError_Returns500WithCode() throws Exception {
		when(solveService.solve(any())).thenThrow(new LlmResponseParseException("bad JSON"));

		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":1}
						""")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("LLM_PARSE_ERROR"))
				.andExpect(jsonPath("$.message").value("bad JSON"));
	}

	@Test
	void solve_UnexpectedException_Returns500WithGenericCode() throws Exception {
		when(solveService.solve(any())).thenThrow(new RuntimeException("Unexpected failure"));

		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":1}
						""")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	// ── SSE streaming endpoint ────────────────────────────────────────────────

	@Test
	void solveStream_ValidRequest_Returns200WithEventStream() throws Exception {
		var result = new SolveResult("Parent guide", "Child script", "{}", List.of("ratio.basic"));
		when(solveService.solve(any())).thenReturn(result);

		mockMvc.perform(post("/api/v1/solve/stream").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":1}
						""")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
	}

	@Test
	void solveStream_BlankQuestion_Returns400() throws Exception {
		mockMvc.perform(post("/api/v1/solve/stream").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"","grade":3}
						""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void solve_SocraticMode_Returns200WithResult() throws Exception {
		var result = new SolveResult("Socratic parent guide", "What do you think 5 + 3 equals? 🤔", "{}",
				List.of("whole_numbers"));
		when(solveService.solve(any())).thenReturn(result);

		mockMvc.perform(post("/api/v1/solve").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"question":"What is 5 + 3?","grade":1,"mode":"SOCRATIC"}
						""")).andExpect(status().isOk())
				.andExpect(jsonPath("$.parentGuide").value("Socratic parent guide"))
				.andExpect(jsonPath("$.childScript").value("What do you think 5 + 3 equals? 🤔"));
	}
}
