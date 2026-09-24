package com.example.toolhub.service.search;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RatingToolSortStrategy implements ToolSortStrategy {

    @Override
    public ToolSortOption option() {
        return ToolSortOption.RATING;
    }

    @Override
    public Sort toSort() {
        // Provisional contract: Role D has not merged the review aggregate yet,
        // so there is no rating column to sort by at the database level.
        // Fall back to newest-first with a stable id tie-break until the
        // average-rating/review-count contract with D is agreed.
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
