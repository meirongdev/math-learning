package com.mathlearning;

import com.mathlearning.service.CacheWarmupService;
import com.mathlearning.service.JwtService;
import com.mathlearning.service.QuestionImportService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * Base class for Spring Boot integration tests. Provides a shared
 * Testcontainers PostgreSQL instance and mocks for all AI-infrastructure beans
 * so tests run without a live Ollama or Redis instance.
 *
 * <p>
 * All subclasses share the same Spring application context and the same
 * container, avoiding redundant container starts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
			.withDatabaseName("mathlearning").withUsername("mathlearning").withPassword("mathlearning");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	// ── Infrastructure mocks shared by all integration tests ─────────────────

	@MockitoBean
	OllamaChatModel ollamaChatModel;

	@MockitoBean
	EmbeddingModel embeddingModel;

	@MockitoBean
	VectorStore vectorStore;

	@MockitoBean
	QuestionImportService questionImportService;

	@MockitoBean
	CacheWarmupService cacheWarmupService;

	@Autowired
	protected JwtService jwtService;

	protected String generateTestToken() {
		return jwtService.generateToken(UUID.randomUUID());
	}

	protected String generateTestToken(UUID userId) {
		return jwtService.generateToken(userId);
	}
}
