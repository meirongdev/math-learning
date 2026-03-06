package com.mathlearning.controller;

import com.mathlearning.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController. Runs against a Testcontainers
 * PostgreSQL database (via AbstractIntegrationTest) — no local infrastructure
 * required.
 */
class AuthControllerTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

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
		String email = "dup-%d@test.com".formatted(System.nanoTime());
		String body = """
				{"email":"%s","password":"pass1234"}
				""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Email already registered"));
	}

	@Test
	void login_ValidCredentials_ReturnsToken() throws Exception {
		String email = "login-%d@test.com".formatted(System.nanoTime());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"pass1234"}
				""".formatted(email))).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"pass1234"}
				""".formatted(email))).andExpect(status().isOk()).andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.userId").exists()).andExpect(jsonPath("$.expiresAt").exists());
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
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}
}
