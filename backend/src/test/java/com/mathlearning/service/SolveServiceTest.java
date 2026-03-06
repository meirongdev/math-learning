package com.mathlearning.service;

import com.mathlearning.agent.MathSolverOrchestrator;
import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.repository.SolveRecordRepository;
import com.mathlearning.repository.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolveServiceTest {

	@Mock
	MathSolverOrchestrator orchestrator;

	@Mock
	SolveRecordRepository solveRecordRepository;

	@Mock
	StudentProfileRepository studentProfileRepository;

	@Mock
	KnowledgeService knowledgeService;

	SolveService solveService;

	@BeforeEach
	void setUp() {
		solveService = new SolveService(orchestrator, solveRecordRepository, studentProfileRepository,
				knowledgeService);
	}

	@Test
	void solve_delegatesToOrchestrator() {
		var request = new SolveRequest("5 + 3 = ?", 1);
		var expected = new SolveResult("guide", "script", "{}", List.of("whole_numbers"));
		when(orchestrator.solve(any())).thenReturn(expected);

		var result = solveService.solve(request);

		assertSame(expected, result);
		verify(orchestrator).solve(request);
	}

	@Test
	void solve_passesRequestThroughToOrchestrator() {
		var request = new SolveRequest("x + 3 = 8, find x", 6);
		var expected = new SolveResult("parent guide", "child script", "{}", List.of("algebra.forming_equation"));
		when(orchestrator.solve(any())).thenReturn(expected);

		var result = solveService.solve(request);

		assertEquals("parent guide", result.parentGuide());
		assertEquals(List.of("algebra.forming_equation"), result.knowledgeTags());
		verify(orchestrator).solve(request);
	}
}
