package com.mathlearning.repository;

import com.mathlearning.model.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

	List<StudentProfile> findByParentIdOrderByCreatedAtDesc(UUID parentId);
}
