package com.example.toolhub.dto.response;

import java.time.Instant;

public record ToolVersionResponse(Long id, Long toolId, String version, String releaseNotes,
                                  Instant releasedAt, Instant createdAt, Instant updatedAt) {
}
