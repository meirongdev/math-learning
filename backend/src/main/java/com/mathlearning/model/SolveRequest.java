package com.mathlearning.model;

import java.util.UUID;

/**
 * Request payload for the solve endpoint.
 *
 * @param question
 *            the math question text
 * @param grade
 *            the student's grade level (1-6)
 * @param studentId
 *            optional UUID of the student profile (nullable for anonymous
 *            usage)
 */
public record SolveRequest(String question, int grade, UUID studentId) {
	public SolveRequest(String question, int grade) {
		this(question, grade, null);
	}
}
