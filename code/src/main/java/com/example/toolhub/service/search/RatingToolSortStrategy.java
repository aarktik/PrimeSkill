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
        // The repository orders by the full-precision review aggregate before pagination.
        // This strategy exposes the stable tie-break; it must not append newest-first.
        return Sort.by(Sort.Direction.ASC, "id");
    }
}
