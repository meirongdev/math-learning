package com.mathlearning.controller;

import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.model.entity.User;
import com.mathlearning.repository.StudentProfileRepository;
import com.mathlearning.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

	public StudentController(StudentProfileRepository studentProfileRepository, UserRepository userRepository) {
		this.studentProfileRepository = studentProfileRepository;
		this.userRepository = userRepository;
	}

	public record CreateStudentRequest(
			@NotBlank(message = "must not be blank") @Size(max = 100, message = "must not exceed 100 characters") String name,
			@Min(value = 1, message = "must be between 1 and 6") @Max(value = 6, message = "must be between 1 and 6") int grade) {
	}

	public record StudentResponse(UUID id, String name, int grade, String createdAt) {
	}

	@PostMapping
	public ResponseEntity<?> createStudent(@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody CreateStudentRequest request) {
		User parent = userRepository.findById(userId).orElseThrow();
		StudentProfile student = StudentProfile.builder().parent(parent).name(request.name()).grade(request.grade())
				.build();
		studentProfileRepository.save(student);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(Map.of("id", student.getId(), "name", student.getName(), "grade", student.getGrade()));
	}

	@GetMapping
	public List<StudentResponse> listStudents(@AuthenticationPrincipal UUID userId) {
		return studentProfileRepository.findByParentIdOrderByCreatedAtDesc(userId).stream()
				.map(s -> new StudentResponse(s.getId(), s.getName(), s.getGrade(), s.getCreatedAt().toString()))
				.toList();
	}
}
