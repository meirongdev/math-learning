package com.mathlearning.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads Singapore math questions from a JSON file into the PgVector store on
 * startup. Only imports if the vector_store table is empty (idempotent).
 */
@Service
public class QuestionImportService {

	private static final Logger log = LoggerFactory.getLogger(QuestionImportService.class);

	private final VectorStore vectorStore;
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public QuestionImportService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
		this.vectorStore = vectorStore;
		this.jdbcTemplate = jdbcTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void importQuestionsOnStartup() {
		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
		if (count != null && count > 0) {
			log.info("Vector store already contains {} documents, skipping import", count);
			return;
		}

		log.info("Vector store is empty, importing question bank...");
		importQuestions();
	}

	public void importQuestions() {
		try {
			var resource = new ClassPathResource("data/sg-math-questions.json");
			try (InputStream is = resource.getInputStream()) {
				List<Map<String, Object>> questions = objectMapper.readValue(is, new TypeReference<>() {
				});

				List<Document> documents = questions.stream().map(this::toDocument).toList();

				vectorStore.add(documents);
				log.info("Successfully imported {} questions into vector store", documents.size());
			}
		} catch (Exception e) {
			log.error("Failed to import question bank", e);
		}
	}

	@SuppressWarnings("unchecked")
	private Document toDocument(Map<String, Object> question) {
		String content = (String) question.get("content");
		Map<String, Object> metadata = (Map<String, Object>) question.get("metadata");

		// Ensure metadata values are strings for filter compatibility
		Map<String, Object> docMetadata = Map.of("grade", metadata.get("grade"), "topic", metadata.get("topic"),
				"difficulty", metadata.get("difficulty"), "source", metadata.get("source"));

		return new Document(content, docMetadata);
	}
}
