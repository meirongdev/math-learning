package com.mathlearning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG retrieval service that searches the vector store for similar math
 * questions. Supports grade-level filtering: only returns questions at or below
 * the queried grade.
 */
@Service
public class RagRetrievalService {

	private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

	private static final int TOP_K = 5;
	private static final double SIMILARITY_THRESHOLD = 0.5;

	private final VectorStore vectorStore;

	public RagRetrievalService(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	/**
	 * Retrieves the top-K most similar questions from the vector store, filtered to
	 * only include questions at or below the given grade level.
	 *
	 * @param question
	 *            the input question text
	 * @param grade
	 *            the student's grade level (1-6)
	 * @return list of similar documents with content and metadata
	 */
	public List<Document> retrieveSimilarQuestions(String question, int grade) {
		log.debug("RAG retrieval for grade <= {} question: {}", grade, question);

		var filterBuilder = new FilterExpressionBuilder();
		var filterExpression = filterBuilder.lte("grade", grade).build();

		SearchRequest searchRequest = SearchRequest.builder().query(question).topK(TOP_K)
				.similarityThreshold(SIMILARITY_THRESHOLD).filterExpression(filterExpression).build();

		List<Document> results = vectorStore.similaritySearch(searchRequest);
		log.info("RAG retrieval returned {} similar questions for grade <= {}", results.size(), grade);
		return results;
	}

	/**
	 * Formats retrieved documents into a context string for injection into prompts.
	 */
	public String formatAsContext(List<Document> documents) {
		if (documents.isEmpty()) {
			return "No similar questions found in the knowledge base.";
		}

		var sb = new StringBuilder("=== Similar Questions from PSLE Question Bank ===\n\n");
		for (int i = 0; i < documents.size(); i++) {
			Document doc = documents.get(i);
			sb.append("Question %d: %s\n".formatted(i + 1, doc.getText()));
			if (doc.getMetadata().containsKey("topic")) {
				sb.append("  Topic: %s\n".formatted(doc.getMetadata().get("topic")));
			}
			if (doc.getMetadata().containsKey("difficulty")) {
				sb.append("  Difficulty: %s\n".formatted(doc.getMetadata().get("difficulty")));
			}
			sb.append("\n");
		}
		return sb.toString();
	}
}
