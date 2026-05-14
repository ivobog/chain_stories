package com.chainreaction.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiStoryAiProvider implements StoryAiProvider {

    private final RestClient restClient;
    private final StoryGenerationJsonParser jsonParser;
    private final String apiKey;
    private final String model;

    @Autowired
    public OpenAiStoryAiProvider(
            RestClient.Builder restClientBuilder,
            StoryGenerationJsonParser jsonParser,
            @Value("${app.ai.openai.api-key:${OPENAI_API_KEY:${AI_API_KEY:}}}") String apiKey,
            @Value("${app.ai.openai.model:gpt-4.1-mini}") String model,
            @Value("${app.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${app.ai.openai.connect-timeout:3s}") String connectTimeout,
            @Value("${app.ai.openai.read-timeout:15s}") String readTimeout) {
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(parseDuration(connectTimeout), parseDuration(readTimeout)))
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.jsonParser = jsonParser;
        this.apiKey = apiKey;
        this.model = model;
    }

    OpenAiStoryAiProvider(
            RestClient restClient,
            StoryGenerationJsonParser jsonParser,
            String apiKey,
            String model) {
        this.restClient = restClient;
        this.jsonParser = jsonParser;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public StoryGenerationResult generate(StoryGenerationPrompt prompt, StoryGenerationRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw providerFailure("OpenAI API key is not configured.");
        }
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(prompt))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw providerFailure("OpenAI provider request failed: HTTP "
                    + exception.getStatusCode().value()
                    + responseErrorMessage(exception.getResponseBodyAsString()));
        } catch (RestClientException exception) {
            throw providerFailure("OpenAI provider request failed: " + exception.getClass().getSimpleName());
        }
        String outputJson = outputText(response);
        if (outputJson == null || outputJson.isBlank()) {
            throw providerFailure("OpenAI response did not include structured output.");
        }
        JsonNode usage = response == null ? null : response.path("usage");
        int promptTokens = usage == null ? 0 : usage.path("input_tokens").asInt(0);
        int completionTokens = usage == null ? 0 : usage.path("output_tokens").asInt(0);
        return jsonParser.parse(outputJson, model, promptTokens, completionTokens);
    }

    private Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    private Map<String, Object> requestBody(StoryGenerationPrompt prompt) {
        return Map.of(
                "model", model,
                "input", List.of(
                        Map.of("role", "system", "content", prompt.systemPrompt()),
                        Map.of("role", "user", "content", prompt.userPrompt())),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "story_generation",
                                "strict", true,
                                "schema", schema())));
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of(
                        "sentence",
                        "usedWord",
                        "tone",
                        "intensity",
                        "safetyLevel",
                        "summary",
                        "storyDirection",
                        "tags"),
                "properties", Map.of(
                        "sentence", Map.of("type", "string"),
                        "usedWord", Map.of("type", "string"),
                        "tone", Map.of("type", "string"),
                        "intensity", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "maximum", 5),
                        "safetyLevel", Map.of("type", "string"),
                        "summary", Map.of("type", "string"),
                        "storyDirection", Map.of("type", "string"),
                        "tags", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"))));
    }

    private String outputText(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return null;
        }
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                if ("output_text".equals(contentItem.path("type").asText())) {
                    return contentItem.path("text").asText(null);
                }
            }
        }
        return null;
    }

    private String responseErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        String compact = responseBody.replaceAll("\\s+", " ").trim();
        int maxLength = 240;
        return " body=" + compact.substring(0, Math.min(maxLength, compact.length()));
    }

    private ApiException providerFailure(String message) {
        return new ApiException(ErrorCode.AI_GENERATION_FAILED, HttpStatus.BAD_GATEWAY, message);
    }
}
