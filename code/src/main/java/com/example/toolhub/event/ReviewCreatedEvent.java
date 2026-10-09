package com.example.toolhub.event;

public record ReviewCreatedEvent(Long reviewId, Long toolId, Long userId) {
}
