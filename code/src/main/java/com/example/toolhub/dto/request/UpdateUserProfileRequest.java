package com.example.toolhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 100,
                message = "Display name must be between 2 and 100 characters")
        String displayName,

        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio,

        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        @Pattern(
                regexp = "^https?://.+$",
                message = "Avatar URL must start with http:// or https://"
        )
        String avatarUrl
) {
}