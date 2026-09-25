package com.example.toolhub.service.impl;

import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.ToolSearchService;
import com.example.toolhub.service.search.ToolSortOption;
import com.example.toolhub.service.search.ToolSortStrategy;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolSearchServiceImpl implements ToolSearchService {
    private static final int MAX_SIZE = 100;

    private final ToolRepository toolRepository;
    private final ToolMapper toolMapper;
    private final List<ToolSortStrategy> strategies;

    public ToolSearchServiceImpl(ToolRepository toolRepository,
                                 ToolMapper toolMapper,
                                 List<ToolSortStrategy> strategies) {
        this.toolRepository = toolRepository;
        this.toolMapper = toolMapper;
        this.strategies = strategies;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ToolResponse> search(String keyword, Long categoryId, List<String> tagSlugs,
                                     String sort, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        int effectiveSize = size;

        String sortValue = sort == null || sort.isBlank() ? ToolSortOption.NEWEST.value() : sort;
        ToolSortOption option = ToolSortOption.from(sortValue);
        String normalizedKeyword = normalizeKeyword(keyword);
        if (option == ToolSortOption.RELEVANCE && normalizedKeyword == null) {
            option = ToolSortOption.NEWEST;
        }
        List<String> normalizedTags = normalizeTags(tagSlugs);
        Sort sortOrder = resolveSort(option);
        Pageable pageable = PageRequest.of(page, effectiveSize, sortOrder);

        if (normalizedTags.isEmpty()) {
            return toolRepository.searchPublished(ToolStatus.PUBLISHED, categoryId,
                    normalizedKeyword, pageable).map(toolMapper::toResponse);
        }
        return toolRepository.searchPublishedWithTags(ToolStatus.PUBLISHED, categoryId,
                normalizedKeyword, normalizedTags, pageable).map(toolMapper::toResponse);
    }

    private Sort resolveSort(ToolSortOption option) {
        return strategies.stream()
                .filter(strategy -> strategy.option() == option)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sort: " + option))
                .toSort();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return escapeLike(keyword.trim());
    }

    private List<String> normalizeTags(List<String> tagSlugs) {
        if (tagSlugs == null) {
            return List.of();
        }
        return tagSlugs.stream()
                .filter(slug -> slug != null && !slug.isBlank())
                .map(slug -> slug.trim().toLowerCase())
                .distinct()
                .toList();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
