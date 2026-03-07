package com.mathlearning.controller;

import com.mathlearning.AbstractIntegrationTest;
import com.mathlearning.model.entity.KnowledgeProgress;
import com.mathlearning.model.entity.SolveRecord;
import com.mathlearning.model.entity.StudentProfile;
import com.mathlearning.model.entity.User;
import com.mathlearning.repository.KnowledgeProgressRepository;
import com.mathlearning.repository.SolveRecordRepository;
import com.mathlearning.repository.StudentProfileRepository;
import com.mathlearning.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StudentProfileRepository studentProfileRepository;

	@Autowired
	SolveRecordRepository solveRecordRepository;

	@Autowired
	KnowledgeProgressRepository knowledgeProgressRepository;

	private String token;
	private UUID userId;
	private StudentProfile student;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.builder().email("student-test-%d@test.com".formatted(System.nanoTime()))
				.password("encoded").build());
		userId = user.getId();
		token = generateTestToken(userId);
		student = studentProfileRepository.save(StudentProfile.builder().parent(user).name("Mia").grade(4).build());
	}

	@Test
	void createStudent_ValidRequest_Returns201() throws Exception {
		mockMvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"name":"Alice","grade":3}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.name").value("Alice")).andExpect(jsonPath("$.grade").value(3));
	}

	@Test
	void listStudents_ReturnsCreatedStudents() throws Exception {
		mockMvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"name":"Bob","grade":5}
						""")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/students").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Bob")).andExpect(jsonPath("$[0].grade").value(5));
	}

	@Test
	void createStudent_NoToken_Returns401() throws Exception {
		mockMvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Alice","grade":3}
				""")).andExpect(status().isUnauthorized());
	}

	@Test
	void createStudent_InvalidGrade_Returns400() throws Exception {
		mockMvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"name":"Alice","grade":7}
						""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createStudent_BlankName_Returns400() throws Exception {
		mockMvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + token).content("""
						{"name":"","grade":3}
						""")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void getAchievements_ReturnsUnlockedAndLockedBadges() throws Exception {
		solveRecordRepository.save(SolveRecord.builder().student(student).questionText("3 + 4")
				.knowledgeTags(List.of("fractions.basic", "whole_numbers")).rating(5)
				.createdAt(OffsetDateTime.now().minusDays(1)).build());
		solveRecordRepository.save(SolveRecord.builder().student(student).questionText("5 + 6")
				.knowledgeTags(List.of("fractions.basic")).rating(4).createdAt(OffsetDateTime.now()).build());
		knowledgeProgressRepository.save(KnowledgeProgress.builder().student(student).knowledgeCode("fractions.basic")
				.masteryLevel("MASTERED").masteryScore(new BigDecimal("0.95")).attemptCount(4).correctCount(4).build());

		mockMvc.perform(get("/api/v1/students/{id}/achievements", student.getId())
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("first-solve"))
				.andExpect(jsonPath("$[0].unlocked").value(true))
				.andExpect(jsonPath("$[4].code").value("fraction-master"))
				.andExpect(jsonPath("$[4].unlocked").value(true))
				.andExpect(jsonPath("$[5].code").value("mastery-builder"))
				.andExpect(jsonPath("$[5].unlocked").value(false));
	}

	@Test
	void getLearningPath_ReturnsFocusNodeAndQuestions() throws Exception {
		knowledgeProgressRepository.save(KnowledgeProgress.builder().student(student).knowledgeCode("fractions")
				.masteryLevel("MASTERED").attemptCount(3).correctCount(3).build());
		knowledgeProgressRepository.save(KnowledgeProgress.builder().student(student).knowledgeCode("frac.add_sub")
				.masteryLevel("FAMILIAR").attemptCount(5).correctCount(2).build());

		mockMvc.perform(get("/api/v1/students/{id}/learning-path", student.getId())
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.focusNode.code").value("frac.add_sub"))
				.andExpect(jsonPath("$.reason").exists())
				.andExpect(jsonPath("$.questions").isArray())
				.andExpect(jsonPath("$.questions.length()").value(1));
	}

	@Test
	void getLearningPath_UnknownStudent_Returns404() throws Exception {
		mockMvc.perform(get("/api/v1/students/{id}/learning-path", UUID.randomUUID())
				.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
	}
}
