package com.mathlearning.controller;

import com.mathlearning.model.entity.User;
import com.mathlearning.repository.UserRepository;
import com.mathlearning.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public record RegisterRequest(String email, String password) {
	}

	public record LoginRequest(String email, String password) {
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));
		}

		User user = User.builder().email(request.email()).password(passwordEncoder.encode(request.password())).build();
		userRepository.save(user);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(Map.of("message", "Registration successful", "userId", user.getId()));
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request) {
		return userRepository.findByEmail(request.email())
				.filter(user -> passwordEncoder.matches(request.password(), user.getPassword())).map(user -> {
					String token = jwtService.generateToken(user.getId());
					return ResponseEntity
							.ok(Map.of("token", token, "userId", user.getId(), "expiresAt",
									jwtService.getExpiration(token).toString()));
				}).orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("error", "Invalid email or password")));
	}
}
