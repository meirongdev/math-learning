package com.mathlearning.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
	}

	@ExceptionHandler(LlmTimeoutException.class)
	public ResponseEntity<ErrorResponse> handleLlmTimeout(LlmTimeoutException ex) {
		log.warn("LLM call timed out", ex);
		return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
				.body(new ErrorResponse("LLM_TIMEOUT", ex.getMessage()));
	}

	@ExceptionHandler(LlmResponseParseException.class)
	public ResponseEntity<ErrorResponse> handleLlmParse(LlmResponseParseException ex) {
		log.error("Failed to parse LLM response", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("LLM_PARSE_ERROR", ex.getMessage()));
	}

	@ExceptionHandler(CallNotPermittedException.class)
	public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(CallNotPermittedException ex) {
		log.warn("Circuit breaker is open, LLM service temporarily unavailable", ex);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("LLM_UNAVAILABLE",
				"LLM service is temporarily unavailable. Please try again later."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("ACCESS_DENIED", "You do not have permission to access this resource"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
	}
}
