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

import java.util.Optional;
import java.util.UUID;

/**
 * Service layer wrapping the solve pipeline with multi-layer caching:
 * <ol>
 * <li>L1 — Redis exact-match cache (question+grade key, 24h TTL)</li>
 * <li>L2 — Semantic cache (pgvector similarity &gt; 0.98 + Caffeine
 * in-memory)</li>
 * <li>L3 — Full LLM agent pipeline</li>
 * </ol>
 */
@Service
public class SolveService {

	private static final Logger log = LoggerFactory.getLogger(SolveService.class);

	private final MathSolverOrchestrator orchestrator;
	private final SolveRecordRepository solveRecordRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final KnowledgeService knowledgeService;
	private final SemanticCacheService semanticCacheService;

	public SolveService(MathSolverOrchestrator orchestrator, SolveRecordRepository solveRecordRepository,
			StudentProfileRepository studentProfileRepository, KnowledgeService knowledgeService,
			SemanticCacheService semanticCacheService) {
		this.orchestrator = orchestrator;
		this.solveRecordRepository = solveRecordRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.knowledgeService = knowledgeService;
		this.semanticCacheService = semanticCacheService;
	}

	/**
	 * Solves a math question. L1 Redis cache is checked first (via @Cacheable). On
	 * L1 miss, the semantic cache (L2) and full LLM pipeline (L3) are tried in
	 * order.
	 */
	@Cacheable(value = "solveResults", key = "#request.question().trim().toLowerCase() + ':' + #request.grade()")
	public SolveResult solve(SolveRequest request) {
		// L2: semantic cache — high-similarity vector match
		Optional<SolveResult> cached = semanticCacheService.findSimilar(request.question(), request.grade());
		if (cached.isPresent()) {
			log.info("Semantic cache hit for grade {} question", request.grade());
			persistRecord(request, cached.get());
			trackKnowledge(request, cached.get());
			return cached.get();
		}

		// L3: full LLM agent pipeline
		log.info("Cache miss - running full agent pipeline for grade {} question", request.grade());
		SolveResult result = orchestrator.solve(request);

		// Store in semantic cache for future similar questions
		semanticCacheService.store(request.question(), request.grade(), result);

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
