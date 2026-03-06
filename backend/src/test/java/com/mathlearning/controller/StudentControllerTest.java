package com.mathlearning.controller;

import com.mathlearning.AbstractIntegrationTest;
import com.mathlearning.model.entity.User;
import com.mathlearning.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	private String token;
	private UUID userId;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.builder().email("student-test-%d@test.com".formatted(System.nanoTime()))
				.password("encoded").build());
		userId = user.getId();
		token = generateTestToken(userId);
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
}
