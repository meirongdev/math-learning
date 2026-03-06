package com.mathlearning.repository;

import com.mathlearning.model.entity.SolveRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SolveRecordRepository extends JpaRepository<SolveRecord, UUID> {

	List<SolveRecord> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

	Page<SolveRecord> findByStudentId(UUID studentId, Pageable pageable);
}
