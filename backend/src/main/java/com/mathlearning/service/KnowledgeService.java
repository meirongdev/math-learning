package com.mathlearning.service;

import com.mathlearning.model.entity.KnowledgeNode;
import com.mathlearning.model.entity.KnowledgeProgress;
import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.repository.KnowledgeNodeRepository;
import com.mathlearning.repository.KnowledgeProgressRepository;
import com.mathlearning.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

	private final KnowledgeProgressRepository knowledgeProgressRepository;
	private final StudentProfileRepository studentProfileRepository;
	private final KnowledgeNodeRepository knowledgeNodeRepository;

	public KnowledgeService(KnowledgeProgressRepository knowledgeProgressRepository,
			StudentProfileRepository studentProfileRepository, KnowledgeNodeRepository knowledgeNodeRepository) {
		this.knowledgeProgressRepository = knowledgeProgressRepository;
		this.studentProfileRepository = studentProfileRepository;
		this.knowledgeNodeRepository = knowledgeNodeRepository;
	}

	public record KnowledgeNodeResponse(String code, String nameEn, String nameZh, String parentCode, int gradeStart,
			List<KnowledgeNodeResponse> children) {
	}

	@Cacheable(value = "knowledgeGraph")
	public List<KnowledgeNodeResponse> getKnowledgeGraph() {
		List<KnowledgeNode> nodes = knowledgeNodeRepository.findAllByOrderBySortOrderAsc();
		return buildTree(nodes);
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

	@Transactional
	public void trackKnowledge(UUID studentId, List<String> knowledgeTags) {
		if (knowledgeTags == null || knowledgeTags.isEmpty()) {
			return;
		}
		StudentProfile student = studentProfileRepository.findById(studentId).orElse(null);
		if (student == null) {
			log.warn("StudentProfile {} not found, skipping knowledge tracking", studentId);
			return;
		}
		// Batch fetch existing progress records (1 query instead of N)
		Map<String, KnowledgeProgress> existingMap = knowledgeProgressRepository
				.findByStudentIdAndKnowledgeCodeIn(studentId, knowledgeTags).stream()
				.collect(java.util.stream.Collectors.toMap(KnowledgeProgress::getKnowledgeCode, p -> p));

		OffsetDateTime now = OffsetDateTime.now();
		List<KnowledgeProgress> toSave = knowledgeTags.stream().map(tag -> {
			KnowledgeProgress progress = existingMap.getOrDefault(tag,
					KnowledgeProgress.builder().student(student).knowledgeCode(tag).build());
			progress.setAttemptCount(progress.getAttemptCount() + 1);
			progress.setUpdatedAt(now);
			return progress;
		}).toList();

		// Batch save (1 flush instead of N)
		knowledgeProgressRepository.saveAll(toSave);
		log.info("Knowledge progress updated for student {} with {} tags", studentId, knowledgeTags.size());
	}

	public List<KnowledgeProgress> getProgress(UUID studentId) {
		return knowledgeProgressRepository.findByStudentIdOrderByAttemptCountDesc(studentId);
	}

	@Transactional
	public void updateMastery(UUID studentId, String knowledgeCode, String masteryLevel) {
		StudentProfile student = studentProfileRepository.findById(studentId).orElse(null);
		if (student == null) {
			log.warn("StudentProfile {} not found, skipping mastery update", studentId);
			return;
		}
		KnowledgeProgress progress = knowledgeProgressRepository
				.findByStudentIdAndKnowledgeCode(studentId, knowledgeCode)
				.orElseGet(() -> KnowledgeProgress.builder().student(student).knowledgeCode(knowledgeCode).build());
		progress.setMasteryLevel(masteryLevel);
		progress.setUpdatedAt(OffsetDateTime.now());
		knowledgeProgressRepository.save(progress);
		log.info("Mastery updated for student {} node {} to {}", studentId, knowledgeCode, masteryLevel);
	}
}
