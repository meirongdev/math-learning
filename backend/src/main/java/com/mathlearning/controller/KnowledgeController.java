package com.mathlearning.controller;

import com.mathlearning.model.entity.KnowledgeProgress;
import com.mathlearning.service.KnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

	private final KnowledgeService knowledgeService;

	public KnowledgeController(KnowledgeService knowledgeService) {
		this.knowledgeService = knowledgeService;
	}

	public record KnowledgeResponse(UUID id, String knowledgeCode, int attemptCount, int correctCount,
			String masteryScore, String updatedAt) {
	}

	@GetMapping("/{studentId}")
	public List<KnowledgeResponse> getKnowledgeProgress(@PathVariable UUID studentId) {
		return knowledgeService.getProgress(studentId).stream()
				.map(kp -> new KnowledgeResponse(kp.getId(), kp.getKnowledgeCode(), kp.getAttemptCount(),
						kp.getCorrectCount(), kp.getMasteryScore().toString(), kp.getUpdatedAt().toString()))
				.toList();
	}
}
