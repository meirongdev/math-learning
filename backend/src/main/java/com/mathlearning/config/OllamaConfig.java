package com.mathlearning.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Workaround for Spring AI 2.0.0-M2 bug: OllamaChatOptions leaks the "think"
 * field into the request options map, causing Ollama to return HTTP 400.
 *
 * This interceptor strips "think" from the options map at the HTTP layer.
 *
 * @see <a href=
 *      "https://github.com/spring-projects/spring-ai/pull/5435">spring-ai#5435</a>
 */
@Configuration
public class OllamaConfig {

	private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Bean
	RestClientCustomizer ollamaThinkFieldFixCustomizer() {
		return restClientBuilder -> restClientBuilder.requestInterceptor((request, body, execution) -> {
			if (body != null && body.length > 0) {
				try {
					var tree = objectMapper.readTree(body);
					if (tree.has("options") && tree.get("options").has("think")) {
						((ObjectNode) tree.get("options")).remove("think");
						log.debug("Removed leaked 'think' field from Ollama options map");
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
