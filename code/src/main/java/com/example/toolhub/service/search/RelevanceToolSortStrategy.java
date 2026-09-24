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
        // Provisional v1 scoring: keyword matches are filtered in the repository
        // query (name > shortDescription > description priority is applied at
        // match time); ordering within matches is newest-first with a stable
        // id tie-break. A CASE-based relevance ranking is a follow-up once the
        // team agrees the exact formula. Without a keyword this strategy is not
        // used — the search service falls back to newest.
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
