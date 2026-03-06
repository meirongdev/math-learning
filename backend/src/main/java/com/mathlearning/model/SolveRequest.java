package com.mathlearning.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for the solve endpoint.
 *
 * @param question
 *            the math question text (1–500 characters)
 * @param grade
 *            the student's grade level (1–6)
 * @param studentId
 *            optional UUID of the student profile (nullable for anonymous
 *            usage)
 */
public record SolveRequest(
		@NotBlank(message = "must not be blank") @Size(max = 500, message = "must not exceed 500 characters") String question,
		@Min(value = 1, message = "must be between 1 and 6") @Max(value = 6, message = "must be between 1 and 6") int grade,
		UUID studentId) {

	public SolveRequest(String question, int grade) {
		this(question, grade, null);
	}
}
