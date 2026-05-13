package com.chainreaction.game.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitWordRequest(
        @NotBlank @Size(max = 40) String word) {
}
