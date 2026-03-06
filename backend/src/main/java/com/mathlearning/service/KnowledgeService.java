package com.mathlearning.service;

import com.mathlearning.model.entity.KnowledgeProgress;
import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.repository.KnowledgeProgressRepository;
import com.mathlearning.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

	private final KnowledgeProgressRepository knowledgeProgressRepository;
	private final StudentProfileRepository studentProfileRepository;

	public KnowledgeService(KnowledgeProgressRepository knowledgeProgressRepository,
			StudentProfileRepository studentProfileRepository) {
		this.knowledgeProgressRepository = knowledgeProgressRepository;
		this.studentProfileRepository = studentProfileRepository;
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
		for (String tag : knowledgeTags) {
			KnowledgeProgress progress = knowledgeProgressRepository.findByStudentIdAndKnowledgeCode(studentId, tag)
					.orElseGet(() -> KnowledgeProgress.builder().student(student).knowledgeCode(tag).build());
			progress.setAttemptCount(progress.getAttemptCount() + 1);
			progress.setUpdatedAt(OffsetDateTime.now());
			knowledgeProgressRepository.save(progress);
		}
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
