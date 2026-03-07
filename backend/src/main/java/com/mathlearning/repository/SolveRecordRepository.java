package com.mathlearning.repository;

import com.mathlearning.model.entity.SolveRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SolveRecordRepository extends JpaRepository<SolveRecord, UUID> {

	List<SolveRecord> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

	Page<SolveRecord> findByStudentId(UUID studentId, Pageable pageable);

	Page<SolveRecord> findByStudentIdAndStudentParentId(UUID studentId, UUID parentId, Pageable pageable);

	Optional<SolveRecord> findByIdAndStudentParentId(UUID id, UUID parentId);

	@Query("""
			SELECT r
			FROM SolveRecord r
			JOIN FETCH r.student s
			WHERE r.id = :id AND s.parent.id = :parentId
			""")
	Optional<SolveRecord> findByIdWithStudentAndParentId(UUID id, UUID parentId);

	@Query(value = """
			SELECT sr.*
			FROM solve_records sr
			JOIN student_profiles sp ON sp.id = sr.student_id
			WHERE sp.parent_id = :parentId
			  AND sr.rating IS NOT NULL
			  AND sr.rating <= 2
			  AND (:studentId IS NULL OR sr.student_id = :studentId)
			ORDER BY sr.created_at DESC
			""", countQuery = """
			SELECT COUNT(*)
			FROM solve_records sr
			JOIN student_profiles sp ON sp.id = sr.student_id
			WHERE sp.parent_id = :parentId
			  AND sr.rating IS NOT NULL
			  AND sr.rating <= 2
			  AND (:studentId IS NULL OR sr.student_id = :studentId)
			""", nativeQuery = true)
	Page<SolveRecord> findMistakes(UUID parentId, UUID studentId, Pageable pageable);
}
