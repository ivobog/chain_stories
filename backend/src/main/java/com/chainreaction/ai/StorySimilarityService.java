package com.chainreaction.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorySimilarityService {

    private final double threshold;

    public StorySimilarityService(
            @Value("${app.word-registry.similarity-threshold:0.78}") double threshold) {
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public void rejectIfTooSimilar(StoryGenerationResult result, StoryGenerationRequest request) {
        if (result == null || request.previousUsages() == null || request.previousUsages().isEmpty()) {
            return;
        }
        for (PreviousWordUsage usage : request.previousUsages()) {
            if (similarity(result.sentence(), usage.generatedSentence()) >= threshold) {
                throw new StorySimilarityRejectionException();
            }
        }
    }

    double similarity(String first, String second) {
        Set<String> firstTokens = tokens(first);
        Set<String> secondTokens = tokens(second);
        if (firstTokens.isEmpty() && secondTokens.isEmpty()) {
            return 1.0;
        }
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return 0.0;
        }
        long intersectionSize = firstTokens.stream()
                .filter(secondTokens::contains)
                .count();
        long unionSize = firstTokens.size() + secondTokens.size() - intersectionSize;
        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }

    private Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}']+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}
