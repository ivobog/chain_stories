package com.chainreaction.ai;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class StoryGenerationJsonParser {

    private final ObjectMapper objectMapper;

    public StoryGenerationJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StoryGenerationResult parse(
            String json,
            String model,
            int promptTokens,
            int completionTokens) {
        try {
            ParsedStoryGeneration parsed = objectMapper.readValue(json, ParsedStoryGeneration.class);
            return new StoryGenerationResult(
                    parsed.sentence(),
                    parsed.usedWord(),
                    parsed.tone(),
                    parsed.intensity(),
                    parsed.safetyLevel(),
                    parsed.summary(),
                    parsed.storyDirection(),
                    parsed.tags(),
                    model,
                    promptTokens,
                    completionTokens);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY,
                    "AI response was not valid structured JSON.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedStoryGeneration(
            String sentence,
            String usedWord,
            String tone,
            int intensity,
            String safetyLevel,
            String summary,
            String storyDirection,
            List<String> tags) {
    }
}
