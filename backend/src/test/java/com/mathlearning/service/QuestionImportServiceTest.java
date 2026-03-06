package com.mathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceTest {

	@Mock
	VectorStore vectorStore;

	@Mock
	JdbcTemplate jdbcTemplate;

	QuestionImportService service;

	@BeforeEach
	void setUp() {
		service = new QuestionImportService(vectorStore, jdbcTemplate, new ObjectMapper());
	}

	// ── importQuestionsOnStartup ─────────────────────────────────────────────

	@Test
	void importOnStartup_whenVectorStoreNotEmpty_skipsImport() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(40L);

		service.importQuestionsOnStartup();

		verify(vectorStore, never()).add(any());
	}

	@Test
	void importOnStartup_whenVectorStoreEmpty_callsImport() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

		service.importQuestionsOnStartup();

		verify(vectorStore, atLeastOnce()).add(any());
	}

	@Test
	void importOnStartup_whenCountIsNull_importsAsFallback() {
		// null count is treated the same as 0 — import runs
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

		service.importQuestionsOnStartup();

		verify(vectorStore, atLeastOnce()).add(any());
	}

	// ── importQuestions ──────────────────────────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	void importQuestions_loadsFromClasspathAndAddsToVectorStore() {
		var captor = ArgumentCaptor.forClass(List.class);

		service.importQuestions();

		verify(vectorStore).add(captor.capture());
		List<Document> added = captor.getValue();
		assertFalse(added.isEmpty(), "should import questions from JSON file");
	}

	@Test
	@SuppressWarnings("unchecked")
	void importQuestions_documentsHaveRequiredMetadata() {
		var captor = ArgumentCaptor.forClass(List.class);

		service.importQuestions();

		verify(vectorStore).add(captor.capture());
		List<Document> added = captor.getValue();
		Document first = added.get(0);
		assertNotNull(first.getText(), "document should have content");
		assertNotNull(first.getMetadata().get("grade"), "document should have grade metadata");
		assertNotNull(first.getMetadata().get("topic"), "document should have topic metadata");
	}

	@Test
	@SuppressWarnings("unchecked")
	void importQuestions_imports40Questions() {
		var captor = ArgumentCaptor.forClass(List.class);

		service.importQuestions();

		verify(vectorStore).add(captor.capture());
		assertEquals(40, captor.getValue().size(), "should import all 40 PSLE questions");
	}
}
