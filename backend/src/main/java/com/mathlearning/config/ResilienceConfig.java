package com.mathlearning.config;

import com.mathlearning.exception.LlmResponseParseException;
import com.mathlearning.exception.LlmTimeoutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j retry and circuit-breaker configuration for LLM calls. Retries
 * use exponential back-off (2 s → 4 s → 8 s). The circuit breaker opens after
 * 50 % failure rate across a sliding window of 10 calls, with a 60 s recovery
 * wait.
 */
@Configuration
public class ResilienceConfig {

	private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

	@Bean
	Retry llmRetry() {
		RetryConfig config = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofSeconds(2))
				.retryExceptions(LlmTimeoutException.class).ignoreExceptions(LlmResponseParseException.class).build();

		Retry retry = Retry.of("llmRetry", config);
		retry.getEventPublisher().onRetry(event -> log.warn("LLM retry attempt #{} after {}",
				event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
		return retry;
	}

	@Bean
	CircuitBreaker llmCircuitBreaker() {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom().failureRateThreshold(50)
				.waitDurationInOpenState(Duration.ofSeconds(60)).slidingWindowSize(10).minimumNumberOfCalls(5)
				.permittedNumberOfCallsInHalfOpenState(3).build();

		CircuitBreaker cb = CircuitBreaker.of("llmCircuitBreaker", config);
		cb.getEventPublisher().onStateTransition(
				event -> log.warn("LLM circuit breaker state transition: {}", event.getStateTransition()));
		return cb;
	}
}
