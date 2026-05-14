package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenAiStoryAiProviderTests {

    private final StoryGenerationJsonParser jsonParser = new StoryGenerationJsonParser(new ObjectMapper());

    @Test
    void sendsStructuredOutputRequestAndMapsResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiStoryAiProvider provider = new OpenAiStoryAiProvider(
                builder
                        .baseUrl("https://api.openai.test")
                        .defaultHeader("Authorization", "Bearer test-key")
                        .build(),
                jsonParser,
                "test-key",
                "gpt-test");

        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model": "gpt-test",
                          "text": {
                            "format": {
                              "type": "json_schema",
                              "name": "story_generation",
                              "strict": true,
                              "schema": {
                                "properties": {
                                  "intensity": {
                                    "type": "integer",
                                    "minimum": 1,
                                    "maximum": 5
                                  }
                                }
                              }
                            }
                          }
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "content": [
                                {
                                  "type": "output_text",
                                  "text": "{\\"sentence\\":\\"The word \\\\\\"dragon\\\\\\" made the teacups applaud.\\",\\"usedWord\\":\\"dragon\\",\\"tone\\":\\"FUNNY\\",\\"intensity\\":2,\\"safetyLevel\\":\\"TEEN\\",\\"summary\\":\\"Teacups applaud the dragon.\\",\\"storyDirection\\":\\"Keep the kitchen strange.\\",\\"tags\\":[\\"dragon\\",\\"teacups\\"]}"
                                }
                              ]
                            }
                          ],
                          "usage": {
                            "input_tokens": 31,
                            "output_tokens": 18
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        StoryGenerationResult result = provider.generate(prompt(), request());

        assertThat(provider.providerName()).isEqualTo("openai");
        assertThat(result.sentence()).isEqualTo("The word \"dragon\" made the teacups applaud.");
        assertThat(result.usedWord()).isEqualTo("dragon");
        assertThat(result.model()).isEqualTo("gpt-test");
        assertThat(result.promptTokens()).isEqualTo(31);
        assertThat(result.completionTokens()).isEqualTo(18);
        assertThat(result.tags()).containsExactly("dragon", "teacups");
        server.verify();
    }

    @Test
    void includesOpenAiHttpFailureDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiStoryAiProvider provider = new OpenAiStoryAiProvider(
                builder
                        .baseUrl("https://api.openai.test")
                        .defaultHeader("Authorization", "Bearer test-key")
                        .build(),
                jsonParser,
                "test-key",
                "gpt-test");

        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withBadRequest().body("""
                        {
                          "error": {
                            "message": "Unsupported schema setting."
                          }
                        }
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generate(prompt(), request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("OpenAI provider request failed: HTTP 400")
                .hasMessageContaining("Unsupported schema setting.");
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyBeforeHttpCall() {
        OpenAiStoryAiProvider provider = new OpenAiStoryAiProvider(
                RestClient.builder(),
                jsonParser,
                " ",
                "gpt-test",
                "https://api.openai.test",
                "1s",
                "5s");

        assertThatThrownBy(() -> provider.generate(prompt(), request()))
                .isInstanceOf(ApiException.class)
                .hasMessage("OpenAI API key is not configured.");
    }

    @Test
    void rejectsResponseWithoutStructuredOutput() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiStoryAiProvider provider = new OpenAiStoryAiProvider(
                builder
                        .baseUrl("https://api.openai.test")
                        .defaultHeader("Authorization", "Bearer test-key")
                        .build(),
                jsonParser,
                "test-key",
                "gpt-test");

        server.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "content": [
                                {
                                  "type": "summary_text",
                                  "text": "not the structured output"
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.generate(prompt(), request()))
                .isInstanceOf(ApiException.class)
                .hasMessage("OpenAI response did not include structured output.");
        server.verify();
    }

    private StoryGenerationPrompt prompt() {
        return new StoryGenerationPrompt("System prompt JSON", "User prompt dragon");
    }

    private StoryGenerationRequest request() {
        return new StoryGenerationRequest(
                "dragon",
                WritingStyle.FUNNY,
                "en",
                SafetyMode.TEEN,
                "Once upon a kettle.",
                List.of());
    }
}
