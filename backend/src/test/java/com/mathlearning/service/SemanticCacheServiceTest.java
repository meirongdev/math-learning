package com.mathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathlearning.model.SolveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticCacheServiceTest {

	@Mock
	VectorStore vectorStore;

	ObjectMapper objectMapper = new ObjectMapper();

	SemanticCacheService semanticCacheService;

	@BeforeEach
	void setUp() {
		semanticCacheService = new SemanticCacheService(vectorStore, objectMapper);
	}

	@Test
	void findSimilar_cacheHit_returnsResult() throws Exception {
		var expected = new SolveResult("guide", "script", "{}", List.of("whole_numbers"));
		String resultJson = objectMapper.writeValueAsString(expected);
		var doc = new Document("5 + 3 = ?", Map.of("type", "semantic_cache", "grade", 1, "cached_result", resultJson));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

		Optional<SolveResult> result = semanticCacheService.findSimilar("5 + 3 = ?", 1);

		assertTrue(result.isPresent());
		assertEquals("guide", result.get().parentGuide());
		assertEquals("script", result.get().childScript());
	}

	@Test
	void findSimilar_cacheMiss_returnsEmpty() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		Optional<SolveResult> result = semanticCacheService.findSimilar("5 + 3 = ?", 1);

		assertTrue(result.isEmpty());
	}

	@Test
	void findSimilar_vectorStoreError_returnsEmpty() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("DB error"));

		Optional<SolveResult> result = semanticCacheService.findSimilar("5 + 3 = ?", 1);

		assertTrue(result.isEmpty());
	}

	@Test
	@SuppressWarnings("unchecked")
	void store_addsDocumentToVectorStore() {
		var result = new SolveResult("guide", "script", "{}", List.of("tag"));

		semanticCacheService.store("5 + 3 = ?", 1, result);

		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(vectorStore).add(captor.capture());

		List<Document> docs = captor.getValue();
		assertEquals(1, docs.size());
		assertEquals("5 + 3 = ?", docs.getFirst().getText());
		assertEquals("semantic_cache", docs.getFirst().getMetadata().get("type"));
		assertEquals(1, docs.getFirst().getMetadata().get("grade"));
	}

	@Test
	void findSimilar_usesCaffeineCache_onSecondCall() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		semanticCacheService.findSimilar("5 + 3 = ?", 1);
		semanticCacheService.findSimilar("5 + 3 = ?", 1);

		// Vector store should only be called once due to Caffeine cache
		verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
	}
}
