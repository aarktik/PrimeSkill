package com.example.toolhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateToolRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 170)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must use lowercase letters, numbers, and hyphens")
        String slug,
        @NotBlank @Size(max = 300) String shortDescription,
        @NotBlank String description,
        @NotNull Long categoryId,
        @Size(max = 500) @Pattern(regexp = "^$|https?://[^\\s]+$", message = "must be an http or https URL")
        String repositoryUrl) {
}
