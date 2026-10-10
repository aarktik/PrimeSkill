package com.example.toolhub.service.search;

import org.springframework.data.domain.Sort;

public interface ToolSortStrategy {
    ToolSortOption option();

    Sort toSort();
}
