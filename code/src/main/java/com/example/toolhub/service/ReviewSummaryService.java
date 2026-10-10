package com.example.toolhub.service;

import com.example.toolhub.dto.response.ReviewSummary;
import java.util.Collection;
import java.util.Map;

public interface ReviewSummaryService {

    Map<Long, ReviewSummary> summarizeByToolIds(Collection<Long> toolIds);
}
