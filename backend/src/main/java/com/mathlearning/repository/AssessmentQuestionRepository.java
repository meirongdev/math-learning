package com.mathlearning.repository;

import com.mathlearning.model.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, UUID> {

	@Query(value = """
			SELECT aq.* FROM assessment_questions aq
			JOIN assessment_question_tags aqt ON aq.id = aqt.question_id
			WHERE (:tag IS NULL OR aqt.node_code = :tag)
			AND (:grade IS NULL OR aq.grade = :grade)
			ORDER BY RANDOM()
			LIMIT :lim
			""", nativeQuery = true)
	List<AssessmentQuestion> findRandomByTagAndGrade(String tag, Integer grade, int lim);
}
