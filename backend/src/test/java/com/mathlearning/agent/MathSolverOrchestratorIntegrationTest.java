package com.mathlearning.agent;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MathSolverOrchestrator. Requires a running Ollama
 * instance with the qwen3.5 model. Skipped in CI unless OLLAMA_AVAILABLE=true
 * is set.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "OLLAMA_AVAILABLE", matches = "true")
class MathSolverOrchestratorIntegrationTest {

	@Autowired
	private MathSolverOrchestrator orchestrator;

	@Test
	void solve_SimpleAddition_ReturnsStructuredResult() {
		var request = new SolveRequest("5+3=?", 1, UUID.randomUUID());

		SolveResult result = orchestrator.solve(request);

		assertNotNull(result);
		assertNotNull(result.parentGuide(), "parentGuide should not be null");
		assertFalse(result.parentGuide().isBlank(), "parentGuide should not be blank");
		assertNotNull(result.childScript(), "childScript should not be null");
		assertFalse(result.childScript().isBlank(), "childScript should not be blank");
		assertNotNull(result.barModelJson(), "barModelJson should not be null");
		assertNotNull(result.knowledgeTags(), "knowledgeTags should not be null");

		System.out.println("=== Parent Guide ===");
		System.out.println(result.parentGuide());
		System.out.println("\n=== Child Script ===");
		System.out.println(result.childScript());
		System.out.println("\n=== Bar Model JSON ===");
		System.out.println(result.barModelJson());
		System.out.println("\n=== Knowledge Tags ===");
		System.out.println(result.knowledgeTags());
	}

	@Test
	void solve_Algebra_ReturnsStructuredResult() {
		var request = new SolveRequest("x+3=8, find x", 6, UUID.randomUUID());

		SolveResult result = orchestrator.solve(request);

		assertNotNull(result);
		assertNotNull(result.parentGuide());
		assertNotNull(result.childScript());
		assertNotNull(result.barModelJson());
		assertNotNull(result.knowledgeTags());
	}
}
