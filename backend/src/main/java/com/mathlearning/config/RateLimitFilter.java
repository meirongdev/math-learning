package com.mathlearning.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP rate limiter for authentication endpoints. Allows at most 10 requests
 * per minute per IP address on /api/v1/auth/ paths.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final int MAX_REQUESTS_PER_MINUTE = 10;

	private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(1)).build();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String ip = request.getRemoteAddr();
		AtomicInteger count = requestCounts.get(ip, k -> new AtomicInteger(0));
		if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("""
					{"code":"TOO_MANY_REQUESTS","message":"Rate limit exceeded. Try again later."}""");
			return;
		}
		filterChain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/v1/auth/");
	}
}
