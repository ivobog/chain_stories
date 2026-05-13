package com.chainreaction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class StoryAiProviderSelectionTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiProviderSelectionTestConfig.class);

    @Test
    void defaultsToMockProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StoryAiProvider.class);
            assertThat(context).hasSingleBean(MockStoryAiProvider.class);
            assertThat(context).doesNotHaveBean(OpenAiStoryAiProvider.class);
            assertThat(context.getBean(StoryAiProvider.class).providerName()).isEqualTo("mock");
        });
    }

    @Test
    void selectsOpenAiProviderWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=openai",
                        "app.ai.openai.api-key=test-key",
                        "app.ai.openai.model=gpt-test",
                        "app.ai.openai.base-url=https://api.openai.test",
                        "app.ai.openai.connect-timeout=1s",
                        "app.ai.openai.read-timeout=5s")
                .run(context -> {
                    assertThat(context).hasSingleBean(StoryAiProvider.class);
                    assertThat(context).hasSingleBean(OpenAiStoryAiProvider.class);
                    assertThat(context).doesNotHaveBean(MockStoryAiProvider.class);
                    assertThat(context.getBean(StoryAiProvider.class).providerName()).isEqualTo("openai");
                });
    }

    @Configuration
    @Import({
            MockStoryAiProvider.class,
            OpenAiStoryAiProvider.class,
            StoryGenerationJsonParser.class
    })
    static class AiProviderSelectionTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
