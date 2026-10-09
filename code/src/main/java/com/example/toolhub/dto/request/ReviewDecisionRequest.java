package com.example.toolhub.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewDecisionRequest(
        @NotNull @PositiveOrZero @JsonDeserialize(using = StrictReviewRevisionDeserializer.class)
        Long expectedReviewRevision) {}
