package com.example.toolhub.repository;

public interface ReviewSummaryProjection {
    Long getToolId();

    Double getAvgRating();

    long getReviewCount();
}
