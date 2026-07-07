package com.mathlearning.controller;

import com.mathlearning.service.KnowledgeService;
import com.mathlearning.service.KnowledgeService.KnowledgeNodeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
			String masteryScore, String masteryLevel, String updatedAt) {
	}

	public record UpdateMasteryRequest(
			@NotBlank @Pattern(regexp = "UNKNOWN|FAMILIAR|MASTERED", message = "must be UNKNOWN, FAMILIAR, or MASTERED") String masteryLevel) {
	}

	@GetMapping(value = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<KnowledgeNodeResponse> getKnowledgeGraph() {
		return knowledgeService.getKnowledgeGraph();
	}

	@GetMapping(value = "/{studentId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<KnowledgeResponse> getKnowledgeProgress(@PathVariable UUID studentId) {
		return knowledgeService.getProgress(studentId).stream()
				.map(kp -> new KnowledgeResponse(kp.getId(), kp.getKnowledgeCode(), kp.getAttemptCount(),
						kp.getCorrectCount(), kp.getMasteryScore().toString(), kp.getMasteryLevel(),
						kp.getUpdatedAt().toString()))
				.toList();
	}

	@GetMapping(value = "/{studentId}/progress", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<KnowledgeResponse> getStudentProgress(@PathVariable UUID studentId) {
		return getKnowledgeProgress(studentId);
	}

	@PutMapping(value = "/{studentId}/progress/{nodeCode}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> updateMastery(@PathVariable UUID studentId, @PathVariable String nodeCode,
			@Valid @RequestBody UpdateMasteryRequest request) {
		knowledgeService.updateMastery(studentId, nodeCode, request.masteryLevel());
		return ResponseEntity.noContent().build();
	}

}
