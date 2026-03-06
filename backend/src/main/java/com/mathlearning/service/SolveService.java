package com.mathlearning.service;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import com.mathlearning.model.entity.SolveRecord;
import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.repository.SolveRecordRepository;
import com.mathlearning.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.mathlearning.agent.MathSolverOrchestrator;

import java.util.UUID;

/**
 * Service layer wrapping the solve pipeline with Redis caching. Cache key is
 * derived from question text + grade level. Identical question+grade
 * combinations return cached results within 24h TTL.
 */
@Service
public class SolveService {

	private static final Logger log = LoggerFactory.getLogger(SolveService.class);

	private final MathSolverOrchestrator orchestrator;
	private final SolveRecordRepository solveRecordRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final KnowledgeService knowledgeService;

	public SolveService(MathSolverOrchestrator orchestrator, SolveRecordRepository solveRecordRepository,
			StudentProfileRepository studentProfileRepository, KnowledgeService knowledgeService) {
		this.orchestrator = orchestrator;
		this.solveRecordRepository = solveRecordRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.knowledgeService = knowledgeService;
	}

	/**
	 * Solves a math question using the multi-agent pipeline. Results are cached by
	 * question+grade for 24 hours.
	 */
	@Cacheable(value = "solveResults", key = "#request.question().trim().toLowerCase() + ':' + #request.grade()")
	public SolveResult solve(SolveRequest request) {
		log.info("Cache miss - running full agent pipeline for grade {} question", request.grade());
		SolveResult result = orchestrator.solve(request);
		persistRecord(request, result);
		trackKnowledge(request, result);
		return result;
	}

	private void persistRecord(SolveRequest request, SolveResult result) {
		UUID studentId = request.studentId();
		if (studentId == null) {
			log.debug("No studentId provided, skipping solve record persistence");
			return;
		}
		StudentProfile student = studentProfileRepository.findById(studentId).orElse(null);
		if (student == null) {
			log.warn("StudentProfile {} not found, skipping solve record persistence", studentId);
			return;
		}
		SolveRecord record = SolveRecord.builder().student(student).questionText(request.question())
				.parentGuide(result.parentGuide()).childScript(result.childScript()).barModelJson(result.barModelJson())
				.knowledgeTags(result.knowledgeTags()).build();
		solveRecordRepository.save(record);
		log.info("Solve record persisted for student {}", studentId);
	}

	private void trackKnowledge(SolveRequest request, SolveResult result) {
		UUID studentId = request.studentId();
		if (studentId != null) {
			knowledgeService.trackKnowledge(studentId, result.knowledgeTags());
		}
	}
}
