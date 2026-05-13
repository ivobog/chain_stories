package com.chainreaction.auth.service;

import com.chainreaction.auth.domain.RefreshToken;

public record IssuedRefreshToken(String plaintextToken, RefreshToken entity) {
}
