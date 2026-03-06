package com.mathlearning.exception;

public class LlmTimeoutException extends RuntimeException {

	public LlmTimeoutException(String message) {
		super(message);
	}

	public LlmTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}
