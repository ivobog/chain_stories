package com.chainreaction.user.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chainreaction.common.security.CurrentUserPrincipal;
import com.chainreaction.user.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserProfileService userProfileService;

    public MeController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return userProfileService.getMe(principal.getUserId());
    }

    @PatchMapping("/profile")
    public MeResponse updateProfile(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.updateProfile(principal.getUserId(), request);
    }

    @DeleteMapping
    public void deleteAccount(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        userProfileService.deleteAccount(principal.getUserId());
    }
}
