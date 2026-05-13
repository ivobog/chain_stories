package com.chainreaction.ai;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.room.domain.SafetyMode;

@Service
public class WordModerationService {

    private static final Set<String> BLOCKED_WORDS = Set.of(
            "kill",
            "murder",
            "suicide",
            "rape",
            "slur",
            "nazi");

    public ModeratedWord moderate(String word, SafetyMode safetyMode) {
        String normalized = normalize(word);
        if (BLOCKED_WORDS.contains(normalized)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Submitted word is not allowed for this room.");
        }
        if (safetyMode == SafetyMode.FAMILY && normalized.matches(".*(blood|violent|weapon).*")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Submitted word is not allowed for family mode.");
        }
        return new ModeratedWord(word, normalized);
    }

    private String normalize(String word) {
        String normalized = word.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[\\p{L}\\p{N}'-]+$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Submit exactly one word.");
        }
        return normalized;
    }
}
