package com.example.toolhub.dto.response;

import com.example.toolhub.domain.enums.Role;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String email,
        Role role,
        String displayName,
        String bio,
        String avatarUrl,
        Instant createdAt
) {
}