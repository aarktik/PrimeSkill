package com.example.toolhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ToolVersionRequest(
        @NotBlank @Size(max = 100) String version,
        @Size(max = 2000) String releaseNotes) {
}
