package com.mathlearning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Workaround for Spring AI 2.0.0-M2 bug: OllamaChatOptions leaks the "think"
 * field into the request options map or top-level body with an invalid type,
 * causing Ollama to return HTTP 400.
 *
 * <p>
 * Two cases handled:
 * <ol>
 * <li>{@code options.think} — leaked into the options map (original bug)</li>
 * <li>Top-level {@code think} — present but not a valid boolean/string
 * value</li>
 * </ol>
 *
 * @see <a href=
 *      "https://github.com/spring-projects/spring-ai/pull/5435">spring-ai#5435</a>
 */
@Configuration
public class OllamaConfig {

	private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);

	private final ObjectMapper objectMapper;

	public OllamaConfig(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Bean
	RestClientCustomizer restClientTimeoutCustomizer() {
		return restClientBuilder -> {
			log.info("Configuring RestClient with 5-minute timeout using JDK HttpClient");
			JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
			requestFactory.setReadTimeout(Duration.ofMinutes(5));
			restClientBuilder.requestFactory(requestFactory);
		};
	}

	@Bean
	RestClientCustomizer ollamaThinkFieldFixCustomizer() {
		return restClientBuilder -> restClientBuilder.requestInterceptor((request, body, execution) -> {
			if (body != null && body.length > 0) {
				try {
					var tree = objectMapper.readTree(body);
					boolean modified = false;

					// Case 1: 'think' leaked into the options map (Spring AI 2.0.0-M2 bug)
					if (tree.has("options") && tree.get("options").has("think")) {
						((ObjectNode) tree.get("options")).remove("think");
						log.debug("Removed leaked 'think' field from Ollama options map");
						modified = true;
					}

					// Case 2: 'think' at top-level body but with an invalid type.
					// Ollama accepts think: true/false or "high"/"medium"/"low".
					// Any other type (null, object, unexpected string) causes HTTP 400.
					if (tree.has("think")) {
						var thinkNode = tree.get("think");
						boolean valid = thinkNode.isBoolean() || (thinkNode.isTextual()
								&& List.of("high", "medium", "low").contains(thinkNode.textValue()));
						if (!valid) {
							((ObjectNode) tree).remove("think");
							log.debug("Removed invalid top-level 'think' field from Ollama request body");
							modified = true;
						}
					}

					if (modified) {
						body = objectMapper.writeValueAsBytes(tree);
					}
				} catch (Exception e) {
					log.trace("Request body is not JSON or could not be parsed, skipping think field fix", e);
				}
			}
			return execution.execute(request, body);
		});
	}

	@Bean
	ChatClient chatClient(OllamaChatModel ollamaChatModel) {
		return ChatClient.builder(ollamaChatModel).build();
	}
}
