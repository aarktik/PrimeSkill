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
        // Provisional contract (same clarity as rating): valid value but no
        // CASE-based ranking yet. Keyword matches are filtered in the repository
        // query; ordering within matches is newest-first with a stable id
        // tie-break. Without a keyword this strategy is not used — the search
        // service falls back to newest (see ToolSearchServiceImpl + handoff to B).
        // Follow-up owner: C (needs agreed formula before DB-level ranking).
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
