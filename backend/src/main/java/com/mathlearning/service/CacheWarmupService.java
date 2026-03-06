package com.mathlearning.service;

import com.mathlearning.model.SolveResult;
import com.mathlearning.model.entity.SolveRecord;
import com.mathlearning.repository.SolveRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pre-warms the semantic cache on startup by loading the most recent solve
 * records from the database and inserting them into the vector-store cache.
 */
@Service
public class CacheWarmupService {

	private static final Logger log = LoggerFactory.getLogger(CacheWarmupService.class);

	private static final int WARMUP_SIZE = 20;

	private final SolveRecordRepository solveRecordRepository;
	private final SemanticCacheService semanticCacheService;

	public CacheWarmupService(SolveRecordRepository solveRecordRepository, SemanticCacheService semanticCacheService) {
		this.solveRecordRepository = solveRecordRepository;
		this.semanticCacheService = semanticCacheService;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void warmupCache() {
		try {
			var pageable = PageRequest.of(0, WARMUP_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
			var recentRecords = solveRecordRepository.findAll(pageable);

			int warmedUp = 0;
			for (SolveRecord record : recentRecords) {
				SolveResult result = new SolveResult(record.getParentGuide(), record.getChildScript(),
						record.getBarModelJson(), record.getKnowledgeTags());
				semanticCacheService.store(record.getQuestionText(), record.getStudent().getGrade(), result);
				warmedUp++;
			}
			log.info("Cache warmup completed: {} solve records pre-cached", warmedUp);
		} catch (Exception e) {
			log.warn("Cache warmup failed, application will continue without pre-cached data", e);
		}
	}
}
