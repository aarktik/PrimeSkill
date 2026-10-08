package com.example.toolhub.dto.response;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long toolId,
        Long authorId,
        String authorDisplayName,
        Short rating,
        String comment,
        Instant createdAt,
        Instant updatedAt) {
}
