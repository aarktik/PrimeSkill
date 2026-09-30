package com.example.toolhub.service.search;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RelevanceToolSortStrategy implements ToolSortStrategy {

    @Override
    public ToolSortOption option() {
        return ToolSortOption.RELEVANCE;
    }

    @Override
    public Sort toSort() {
        // Relevance order is defined in the repository query; this is only for no-keyword fallback.
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
