package com.mathlearning.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

	@Mock
	VectorStore vectorStore;

	@InjectMocks
	RagRetrievalService service;

	// ── retrieveSimilarQuestions ─────────────────────────────────────────────

	@Test
	void retrieveSimilarQuestions_callsVectorStoreWithSearchRequest() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		service.retrieveSimilarQuestions("5 + 3 = ?", 3);

		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void retrieveSimilarQuestions_returnsDocumentsFromVectorStore() {
		var doc = new Document("Sample PSLE question", Map.of("grade", 3));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

		var results = service.retrieveSimilarQuestions("5 + 3 = ?", 3);

		assertEquals(1, results.size());
		assertEquals("Sample PSLE question", results.get(0).getText());
	}

	@Test
	void retrieveSimilarQuestions_passesGradeInFilter() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
		var captor = ArgumentCaptor.forClass(SearchRequest.class);

		service.retrieveSimilarQuestions("ratio question", 5);

		verify(vectorStore).similaritySearch(captor.capture());
		var request = captor.getValue();
		assertEquals("ratio question", request.getQuery());
		assertNotNull(request.getFilterExpression(), "filter expression must be set for grade");
	}

	@Test
	void retrieveSimilarQuestions_emptyResultsReturnedAsEmptyList() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		var results = service.retrieveSimilarQuestions("hard question", 6);

		assertTrue(results.isEmpty());
	}

	// ── formatAsContext ──────────────────────────────────────────────────────

	@Test
	void formatAsContext_emptyList_returnsNoSimilarMessage() {
		var result = service.formatAsContext(List.of());
		assertEquals("No similar questions found in the knowledge base.", result);
	}

	@Test
	void formatAsContext_withTopicAndDifficulty_includesMetadata() {
		var doc = new Document("Amy has 24 sweets.", Map.of("topic", "fractions", "difficulty", "medium"));

		var result = service.formatAsContext(List.of(doc));

		assertTrue(result.contains("Amy has 24 sweets."), "should include document text");
		assertTrue(result.contains("Topic: fractions"), "should include topic metadata");
		assertTrue(result.contains("Difficulty: medium"), "should include difficulty metadata");
	}

	@Test
	void formatAsContext_withoutMetadata_omitsTopicAndDifficulty() {
		var doc = new Document("Plain question", Map.of());

		var result = service.formatAsContext(List.of(doc));

		assertTrue(result.contains("Plain question"));
		assertFalse(result.contains("Topic:"), "should not show Topic when metadata absent");
		assertFalse(result.contains("Difficulty:"), "should not show Difficulty when metadata absent");
	}

	@Test
	void formatAsContext_multipleDocuments_numbersEachQuestion() {
		var doc1 = new Document("First question", Map.of());
		var doc2 = new Document("Second question", Map.of());
		var doc3 = new Document("Third question", Map.of());

		var result = service.formatAsContext(List.of(doc1, doc2, doc3));

		assertTrue(result.contains("Question 1: First question"));
		assertTrue(result.contains("Question 2: Second question"));
		assertTrue(result.contains("Question 3: Third question"));
	}

	@Test
	void formatAsContext_containsSectionHeader() {
		var doc = new Document("Some question", Map.of());

		var result = service.formatAsContext(List.of(doc));

		assertTrue(result.contains("Similar Questions from PSLE Question Bank"));
	}
}
