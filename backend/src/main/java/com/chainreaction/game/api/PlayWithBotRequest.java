package com.chainreaction.game.api;

import com.chainreaction.room.domain.SafetyMode;
import com.chainreaction.room.domain.WritingStyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlayWithBotRequest(
        @NotNull WritingStyle writingStyle,
        @NotBlank @Size(min = 2, max = 16) @Pattern(regexp = "^[a-zA-Z-]+$") String language,
        @NotNull SafetyMode safetyMode,
        @Min(2) @Max(50) int turnLimit,
        @Min(15) @Max(300) int turnTimeoutSeconds) {
}
