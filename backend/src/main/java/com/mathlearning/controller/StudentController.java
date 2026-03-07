package com.mathlearning.controller;

import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.model.entity.User;
import com.mathlearning.repository.StudentProfileRepository;
import com.mathlearning.repository.UserRepository;
import com.mathlearning.service.StudentPhase10Service;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

	private final StudentProfileRepository studentProfileRepository;
	private final UserRepository userRepository;
	private final StudentPhase10Service studentPhase10Service;

	public StudentController(StudentProfileRepository studentProfileRepository, UserRepository userRepository,
			StudentPhase10Service studentPhase10Service) {
		this.studentProfileRepository = studentProfileRepository;
		this.userRepository = userRepository;
		this.studentPhase10Service = studentPhase10Service;
	}

	public record CreateStudentRequest(
			@NotBlank(message = "must not be blank") @Size(max = 100, message = "must not exceed 100 characters") String name,
			@Min(value = 1, message = "must be between 1 and 6") @Max(value = 6, message = "must be between 1 and 6") int grade) {
	}

	public record StudentResponse(UUID id, String name, int grade, String createdAt) {
	}

	public record AchievementResponse(String code, String title, String description, String icon, boolean unlocked,
			int currentValue, int targetValue) {
	}

	public record LearningNodeResponse(String code, String nameEn, String nameZh, String masteryLevel,
			int gradeStart) {
	}

	public record RecommendedQuestionResponse(UUID id, String questionText, int grade, String difficulty,
			String answerHint) {
	}

	public record LearningPathResponse(String summary, String reason, LearningNodeResponse focusNode,
			LearningNodeResponse prerequisiteNode, List<RecommendedQuestionResponse> questions) {
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> createStudent(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody CreateStudentRequest request) {
		User parent = userRepository.findById(userId).orElseThrow();
		StudentProfile student = StudentProfile.builder().parent(parent).name(request.name()).grade(request.grade())
				.build();
		studentProfileRepository.save(student);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(Map.of("id", student.getId(), "name", student.getName(), "grade", student.getGrade()));
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<StudentResponse> listStudents(@AuthenticationPrincipal UUID userId) {
		return studentProfileRepository.findByParentIdOrderByCreatedAtDesc(userId).stream()
				.map(s -> new StudentResponse(s.getId(), s.getName(), s.getGrade(), s.getCreatedAt().toString()))
				.toList();
	}

	@GetMapping(value = "/{id}/achievements", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<AchievementResponse>> getAchievements(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID id) {
		return studentProfileRepository.findByIdAndParentId(id, userId).map(student -> ResponseEntity.ok(
				studentPhase10Service.getAchievements(id).stream().map(a -> new AchievementResponse(a.code(), a.title(),
						a.description(), a.icon(), a.unlocked(), a.currentValue(), a.targetValue())).toList()))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/{id}/learning-path", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LearningPathResponse> getLearningPath(@AuthenticationPrincipal UUID userId,
			@PathVariable UUID id) {
		return studentProfileRepository.findByIdAndParentId(id, userId).map(student -> {
			var path = studentPhase10Service.getLearningPath(id, student.getGrade());
			return ResponseEntity.ok(new LearningPathResponse(path.summary(), path.reason(),
					toLearningNodeResponse(path.focusNode()), toLearningNodeResponse(path.prerequisiteNode()),
					path.questions().stream().map(q -> new RecommendedQuestionResponse(q.id(), q.questionText(),
							q.grade(), q.difficulty(), q.answerHint())).toList()));
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteStudent(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
		return studentProfileRepository.findById(id).filter(s -> s.getParent().getId().equals(userId)).map(s -> {
			studentProfileRepository.delete(s);
			return ResponseEntity.noContent().<Void>build();
		}).orElse(ResponseEntity.notFound().build());
	}

	private LearningNodeResponse toLearningNodeResponse(StudentPhase10Service.LearningNode node) {
		if (node == null) {
			return null;
		}
		return new LearningNodeResponse(node.code(), node.nameEn(), node.nameZh(), node.masteryLevel(),
				node.gradeStart());
	}
}
