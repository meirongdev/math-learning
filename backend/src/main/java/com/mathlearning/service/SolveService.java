package com.mathlearning.service;

import com.mathlearning.model.SolveRequest;
import com.mathlearning.model.SolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.mathlearning.agent.MathSolverOrchestrator;

/**
 * Service layer wrapping the solve pipeline with Redis caching.
 * Cache key is derived from question text + grade level.
 * Identical question+grade combinations return cached results within 24h TTL.
 */
@Service
public class SolveService {

    private static final Logger log = LoggerFactory.getLogger(SolveService.class);

    private final MathSolverOrchestrator orchestrator;

    public SolveService(MathSolverOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Solves a math question using the multi-agent pipeline.
     * Results are cached by question+grade for 24 hours.
     */
    @Cacheable(value = "solveResults", key = "#request.question().trim().toLowerCase() + ':' + #request.grade()")
    public SolveResult solve(SolveRequest request) {
        log.info("Cache miss - running full agent pipeline for grade {} question", request.grade());
        return orchestrator.solve(request);
    }
}
