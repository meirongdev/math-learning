package com.mathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mathlearning.model.SolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Semantic cache backed by pgvector. Before invoking the LLM pipeline, callers
 * check this cache for a previously-solved question with cosine similarity &gt;
 * 0.98. An in-memory Caffeine cache acts as a secondary layer to avoid repeated
 * embedding + vector-store lookups for the same question text.
 */
@Service
public class SemanticCacheService {

	private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

	private static final double CACHE_SIMILARITY_THRESHOLD = 0.98;

	private final VectorStore vectorStore;
	private final ObjectMapper objectMapper;

	/** In-memory secondary cache: normalized question+grade → cached result. */
	private final Cache<String, Optional<SolveResult>> lookupCache = Caffeine.newBuilder().maximumSize(1000)
			.expireAfterWrite(Duration.ofHours(24)).build();

	public SemanticCacheService(VectorStore vectorStore, ObjectMapper objectMapper) {
		this.vectorStore = vectorStore;
		this.objectMapper = objectMapper;
	}

	/**
	 * Searches for a semantically similar cached solve result. Returns
	 * {@link Optional#empty()} on cache miss or any error.
	 */
	public Optional<SolveResult> findSimilar(String question, int grade) {
		String cacheKey = question.trim().toLowerCase() + ":" + grade;
		return lookupCache.get(cacheKey, _ -> doVectorSearch(question, grade));
	}

	private Optional<SolveResult> doVectorSearch(String question, int grade) {
		try {
			var filterBuilder = new FilterExpressionBuilder();
			var filter = filterBuilder.and(filterBuilder.eq("type", "semantic_cache"), filterBuilder.eq("grade", grade))
					.build();

			SearchRequest searchRequest = SearchRequest.builder().query(question).topK(1)
					.similarityThreshold(CACHE_SIMILARITY_THRESHOLD).filterExpression(filter).build();

			List<Document> results = vectorStore.similaritySearch(searchRequest);
			if (!results.isEmpty()) {
				Document doc = results.getFirst();
				String resultJson = (String) doc.getMetadata().get("cached_result");
				if (resultJson != null) {
					SolveResult result = objectMapper.readValue(resultJson, SolveResult.class);
					log.info("Semantic cache hit (similarity > {})", CACHE_SIMILARITY_THRESHOLD);
					return Optional.of(result);
				}
			}
		} catch (Exception e) {
			log.warn("Semantic cache lookup failed, proceeding without cache", e);
		}
		return Optional.empty();
	}

	/**
	 * Stores a solve result in the vector-store semantic cache and updates the
	 * in-memory secondary cache.
	 */
	public void store(String question, int grade, SolveResult result) {
		try {
			String resultJson = objectMapper.writeValueAsString(result);
			Document cacheDoc = new Document(question,
					Map.of("type", "semantic_cache", "grade", grade, "cached_result", resultJson));
			vectorStore.add(List.of(cacheDoc));
			log.info("Stored solve result in semantic cache for grade {} question", grade);

			String cacheKey = question.trim().toLowerCase() + ":" + grade;
			lookupCache.put(cacheKey, Optional.of(result));
		} catch (Exception e) {
			log.warn("Failed to store result in semantic cache", e);
		}
	}
}
