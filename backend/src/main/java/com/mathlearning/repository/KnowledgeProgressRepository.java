package com.mathlearning.repository;

import com.mathlearning.model.entity.KnowledgeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeProgressRepository extends JpaRepository<KnowledgeProgress, UUID> {

	Optional<KnowledgeProgress> findByStudentIdAndKnowledgeCode(UUID studentId, String knowledgeCode);

	List<KnowledgeProgress> findByStudentIdOrderByAttemptCountDesc(UUID studentId);
}
