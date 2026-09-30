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
        return Sort.by(Sort.Direction.DESC, "viewCount")
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
