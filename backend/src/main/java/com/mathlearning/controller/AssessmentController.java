package com.mathlearning.controller;

import com.mathlearning.repository.AssessmentQuestionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questions")
public class AssessmentController {

	private final AssessmentQuestionRepository assessmentQuestionRepository;

	public AssessmentController(AssessmentQuestionRepository assessmentQuestionRepository) {
		this.assessmentQuestionRepository = assessmentQuestionRepository;
	}

	public record QuestionResponse(UUID id, String questionText, int grade, String difficulty, String answerHint) {
	}

	@GetMapping
	public List<QuestionResponse> getQuestions(@RequestParam(required = false) String tag,
			@RequestParam(required = false) Integer grade, @RequestParam(defaultValue = "10") int limit) {
		return assessmentQuestionRepository.findRandomByTagAndGrade(tag, grade, Math.min(limit, 50)).stream()
				.map(q -> new QuestionResponse(q.getId(), q.getQuestionText(), q.getGrade(), q.getDifficulty(),
						q.getAnswerHint()))
				.toList();
	}
}
