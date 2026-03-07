package com.mathlearning.repository;

import com.mathlearning.model.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

	List<StudentProfile> findByParentIdOrderByCreatedAtDesc(UUID parentId);

	Optional<StudentProfile> findByIdAndParentId(UUID id, UUID parentId);
}
