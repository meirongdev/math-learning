package com.mathlearning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void register_NewUser_Returns201() throws Exception {
		String email = "auth-test-%d@test.com".formatted(System.nanoTime());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"pass1234"}
				""".formatted(email))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value("Registration successful"))
				.andExpect(jsonPath("$.userId").exists());
	}

	@Test
	void register_DuplicateEmail_Returns409() throws Exception {
		String email = "dup-test-%d@test.com".formatted(System.nanoTime());
		String body = """
				{"email":"%s","password":"pass1234"}
				""".formatted(email);

		// First registration
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		// Duplicate
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Email already registered"));
	}

	@Test
	void login_ValidCredentials_ReturnsToken() throws Exception {
		String email = "login-test-%d@test.com".formatted(System.nanoTime());
		// Register first
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"pass1234"}
				""".formatted(email))).andExpect(status().isCreated());

		// Login
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"pass1234"}
				""".formatted(email))).andExpect(status().isOk()).andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.userId").exists());
	}

	@Test
	void login_InvalidPassword_Returns401() throws Exception {
		String email = "bad-login-%d@test.com".formatted(System.nanoTime());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"pass1234"}
				""".formatted(email))).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"wrongpass"}
				""".formatted(email))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Invalid email or password"));
	}
}
