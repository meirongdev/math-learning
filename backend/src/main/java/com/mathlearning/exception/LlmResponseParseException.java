package com.mathlearning.exception;

public class LlmResponseParseException extends RuntimeException {

	public LlmResponseParseException(String message) {
		super(message);
	}

	public LlmResponseParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
