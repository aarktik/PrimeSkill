package com.example.toolhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must use lowercase letters, numbers, and hyphens")
        String slug,
        @Size(max = 500) String description) {
}
