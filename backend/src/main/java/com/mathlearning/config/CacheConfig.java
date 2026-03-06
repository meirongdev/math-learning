package com.mathlearning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathlearning.model.SolveResult;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis cache configuration. Caches AI solve results for 24 hours keyed by
 * question + grade.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, SolveResult.class);

		RedisCacheConfiguration solveResultsConfig = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofHours(24))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
				.disableCachingNullValues();

		return RedisCacheManager.builder(connectionFactory).withCacheConfiguration("solveResults", solveResultsConfig)
				.build();
	}
}
