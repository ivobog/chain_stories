package com.chainreaction.ai;

import org.springframework.stereotype.Component;

@Component
public class WordSuggestionPromptBuilder {

    public WordSuggestionPrompt build(WordSuggestionRequest request) {
        String systemPrompt = """
                Suggest exactly one playable word for the current player.
                Return only one safe word that fits the room style and language.
                Do not return a phrase, punctuation, unsafe term, or a previously accepted word.
                The player may edit the suggestion before submitting it.
                """;
        String userPrompt = """
                Writing style: %s
                Language: %s
                Safety mode: %s
                Previously accepted words:
                %s
                Current story:
                %s
                """.formatted(
                request.writingStyle(),
                request.language(),
                request.safetyMode(),
                previousWordsContext(request),
                request.currentStory());
        return new WordSuggestionPrompt(systemPrompt.strip(), userPrompt.strip());
    }

    private String previousWordsContext(WordSuggestionRequest request) {
        if (request.previousWords() == null || request.previousWords().isEmpty()) {
            return "None yet.";
        }
        return String.join(", ", request.previousWords());
    }
}
