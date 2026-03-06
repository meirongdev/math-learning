package com.mathlearning.repository;

import com.mathlearning.model.entity.KnowledgeNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, String> {

	List<KnowledgeNode> findAllByOrderBySortOrderAsc();
}
