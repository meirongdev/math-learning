package com.mathlearning.controller;

import com.mathlearning.model.entity.KnowledgeNode;
import com.mathlearning.repository.KnowledgeNodeRepository;
import com.mathlearning.service.KnowledgeService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

	private final KnowledgeService knowledgeService;
	private final KnowledgeNodeRepository knowledgeNodeRepository;

	public KnowledgeController(KnowledgeService knowledgeService, KnowledgeNodeRepository knowledgeNodeRepository) {
		this.knowledgeService = knowledgeService;
		this.knowledgeNodeRepository = knowledgeNodeRepository;
	}

	public record KnowledgeResponse(UUID id, String knowledgeCode, int attemptCount, int correctCount,
			String masteryScore, String masteryLevel, String updatedAt) {
	}

	public record KnowledgeNodeResponse(String code, String nameEn, String nameZh, String parentCode, int gradeStart,
			List<KnowledgeNodeResponse> children) {
	}

	public record UpdateMasteryRequest(
			@NotBlank @Pattern(regexp = "UNKNOWN|FAMILIAR|MASTERED", message = "must be UNKNOWN, FAMILIAR, or MASTERED") String masteryLevel) {
	}

	@GetMapping(value = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<KnowledgeNodeResponse> getKnowledgeGraph() {
		List<KnowledgeNode> nodes = knowledgeNodeRepository.findAllByOrderBySortOrderAsc();
		return buildTree(nodes);
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

	private List<KnowledgeNodeResponse> buildTree(List<KnowledgeNode> nodes) {
		Map<String, List<KnowledgeNode>> childrenMap = new LinkedHashMap<>();
		List<KnowledgeNode> roots = new ArrayList<>();

		for (KnowledgeNode node : nodes) {
			if (node.getParentCode() == null) {
				roots.add(node);
			} else {
				childrenMap.computeIfAbsent(node.getParentCode(), k -> new ArrayList<>()).add(node);
			}
		}

		return roots.stream().map(n -> toResponse(n, childrenMap)).toList();
	}

	private KnowledgeNodeResponse toResponse(KnowledgeNode node, Map<String, List<KnowledgeNode>> childrenMap) {
		List<KnowledgeNodeResponse> children = childrenMap.getOrDefault(node.getCode(), List.of()).stream()
				.map(n -> toResponse(n, childrenMap)).toList();
		return new KnowledgeNodeResponse(node.getCode(), node.getNameEn(), node.getNameZh(), node.getParentCode(),
				node.getGradeStart(), children);
	}
}
