package com.chainreaction.auth.service;

import com.chainreaction.auth.domain.PasswordResetToken;

public record IssuedPasswordResetToken(String plaintextToken, PasswordResetToken entity) {
}
