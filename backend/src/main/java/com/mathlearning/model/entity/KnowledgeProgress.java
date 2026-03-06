package com.mathlearning.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "knowledge_progress", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id",
		"knowledge_code" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeProgress {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id", nullable = false)
	private StudentProfile student;

	@Column(name = "knowledge_code", nullable = false, length = 50)
	private String knowledgeCode;

	@Column(name = "mastery_score", precision = 5, scale = 2)
	@Builder.Default
	private BigDecimal masteryScore = BigDecimal.ZERO;

	@Column(name = "attempt_count")
	@Builder.Default
	private int attemptCount = 0;

	@Column(name = "correct_count")
	@Builder.Default
	private int correctCount = 0;

	@Column(name = "updated_at")
	@Builder.Default
	private OffsetDateTime updatedAt = OffsetDateTime.now();
}
