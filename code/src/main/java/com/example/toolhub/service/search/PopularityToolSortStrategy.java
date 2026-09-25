package com.example.toolhub.service.search;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PopularityToolSortStrategy implements ToolSortStrategy {

    @Override
    public ToolSortOption option() {
        return ToolSortOption.POPULAR;
    }

    @Override
    public Sort toSort() {
        // Sorts by viewCount only (read path owned by C).
        // Increment flow owner: B (on GET /api/v1/tools/{idOrSlug} detail view).
        // Until B implements increment, popular order reflects current counts.
        return Sort.by(Sort.Direction.DESC, "viewCount")
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
