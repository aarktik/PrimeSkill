package com.example.toolhub.service.impl;

import com.example.toolhub.dto.response.ReviewSummary;
import com.example.toolhub.repository.ReviewRepository;
import com.example.toolhub.service.ReviewSummaryService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewSummaryServiceImpl implements ReviewSummaryService {

    private final ReviewRepository reviewRepository;

    public ReviewSummaryServiceImpl(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ReviewSummary> summarizeByToolIds(Collection<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Map.of();
        }

        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        for (Long toolId : toolIds) {
            if (toolId != null) {
                requestedIds.add(toolId);
            }
        }
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ReviewSummary> summaries = new LinkedHashMap<>();
        requestedIds.forEach(toolId -> summaries.put(toolId, new ReviewSummary(toolId, null, 0)));
        reviewRepository.summarizeByToolIds(requestedIds).forEach(row ->
                summaries.put(row.getToolId(), new ReviewSummary(
                        row.getToolId(), row.getAvgRating(), row.getReviewCount())));
        return Map.copyOf(summaries);
    }
}
